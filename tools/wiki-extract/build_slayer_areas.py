#!/usr/bin/env python3
"""Builds src/main/resources/slayer_areas.json from sourced inputs.

Inputs
  ../cache-dump/out/slayer_tables.json   Game cache dump: every SlayerArea row (area id, row name, name)
                                         and Konar's allowed areas per task, with task ids.
  runelite_map_data.tsv                  RuneLite 1.13.1 world map data (DumpRuneLiteMapData.java).
  cache/*.txt                            OSRS Wiki wikitext fetched by fetch_pages.py.
  curated.json                           Hand-picked inputs, each with its source: location keys and
                                         labels, wiki name aliases, entrances, facts and notes.
  access.json                            What each place needs to get in, read from the wiki, each with
                                         the quoted sentence or infobox field it comes from.
  work/walkable.tsv, work/reachable.tsv  Optional. check_walkable.sh output for work/candidates.tsv.

Kill spots come only from LocLine templates on monster pages, never from estimates. Each cluster of
spawns is represented by its medoid, which is one of the listed spawn tiles. When the check files
exist, a spawn list is used only if most of its tiles are walkable in Shortest Path's collision map,
and only tiles that Shortest Path can reach through its own transport tables are kept.

Usage: python build_slayer_areas.py   (writes work/candidates.tsv, the JSON and report.txt)
"""
import json
import os
import re
import sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wikiparse import find_templates, infobox_field, links, map_params, parse_tables, plain_text, strip_comments, yesno  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(HERE, '..', '..'))
CACHE = os.path.join(HERE, 'cache')
WORK = os.path.join(HERE, 'work')
DUMP = os.path.join(PROJECT, 'tools', 'cache-dump', 'out', 'slayer_tables.json')
RL_MAP = os.path.join(HERE, 'runelite_map_data.tsv')
OUT_JSON = os.path.join(PROJECT, 'src', 'main', 'resources', 'slayer_areas.json')
REPORT = os.path.join(HERE, 'report.txt')
ACCESS = os.path.join(HERE, 'access.json')
ACCESS_FIELDS = ('questsFinished', 'questsStarted', 'skills', 'note')
WIKI = 'https://oldschool.runescape.wiki/w/'

CLUSTER_GAP = 12    # spawns closer than this many tiles (Chebyshev, same plane) share a cluster
MAX_SPOTS = 6       # kill spots kept per task and location, largest clusters first
RELIABLE_SHARE = 0.6  # share of a LocLine's tiles that must be walkable for the list to be used
ENTRANCE_REACH = 8    # an entrance is routable when Shortest Path can reach a tile this close to it

# The Wilderness as RuneLite 1.13.1 defines it: NpcAggroAreaPlugin's WILDERNESS_ABOVE_GROUND and
# WILDERNESS_UNDERGROUND (x, y, width, height). Its isInArea2D check ignores the plane.
RUNELITE_WILDERNESS = [(2944, 3523, 448, 448), (2944, 9918, 320, 442)]
RUNELITE_WILDERNESS_SOURCE = ('RuneLite 1.13.1 Wilderness areas (NpcAggroAreaPlugin WILDERNESS_ABOVE_GROUND '
                              '2944,3523 448x448 and WILDERNESS_UNDERGROUND 2944,9918 320x442)')


def in_runelite_wilderness(p):
    return any(x <= p[0] < x + w and y <= p[1] < y + h for x, y, w, h in RUNELITE_WILDERNESS)


def slug(text):
    return re.sub(r'[^a-z0-9]+', '_', text.lower().replace("'", '')).strip('_')


def clean_location_text(text):
    text = re.sub(r'<ref[^>]*/>', '', text)
    text = re.sub(r'<ref[^>]*>.*?</ref>', '', text, flags=re.S)
    text = re.sub(r'<[^>]+>', '', text)
    for _ in range(3):
        text = re.sub(r'\{\{[^{}]*\}\}', '', text)
    text = re.sub(r'\[\[([^\]|]+)\|([^\]]+)\]\]', r'\2', text)
    text = re.sub(r'\[\[([^\]]+)\]\]', r'\1', text)
    text = re.sub(r'\(\s*\)', '', text)
    return re.sub(r'\s+', ' ', text).strip(' ,-')


def page_text(manifest, title):
    entry = manifest.get(title)
    if not entry:
        return None, None
    final = entry['final']
    path = os.path.join(CACHE, re.sub(r'[^A-Za-z0-9_.-]+', '_', final) + '.txt')
    if not os.path.exists(path):
        return None, None
    return strip_comments(open(path, encoding='utf-8').read()), final


def wiki_url(title):
    return WIKI + title.replace(' ', '_')


def load_runelite_points():
    points = {}
    for line in open(RL_MAP, encoding='utf-8'):
        parts = line.rstrip('\n').split('\t')
        fields = dict(p.split('=', 1) for p in parts[2:] if '=' in p)
        for field in ('Location', 'Target'):
            m = re.match(r'WorldPoint\(x=(\d+), y=(\d+), plane=(\d+)\)', fields.get(field, ''))
            if m:
                points[(parts[0], parts[1], field)] = {
                    'point': [int(m.group(1)), int(m.group(2)), int(m.group(3))],
                    'tooltip': fields.get('Tooltip') or fields.get('Code') or ''}
    return points


def load_check(path, candidates, report, what):
    if not os.path.exists(path):
        report.append('NOTE: %s missing, kill spots are not checked for being %s.' % (os.path.basename(path), what))
        return None
    result = {}
    for line in open(path, encoding='utf-8'):
        x, y, z, ok = line.split()[:4]
        result[(int(x), int(y), int(z))] = ok == '1'
    unchecked = [p for p in candidates if p not in result]
    if unchecked:
        report.append('WARNING: %d spawn tiles were not checked for being %s. Run check_walkable.sh again.' % (
            len(unchecked), what))
        return None
    return result


def chebyshev(a, b):
    return max(abs(a[0] - b[0]), abs(a[1] - b[1]))


def clusters(points):
    """Single-linkage clusters of same-plane points."""
    remaining = list(points)
    groups = []
    while remaining:
        group = [remaining.pop(0)]
        grew = True
        while grew:
            grew = False
            for p in list(remaining):
                if any(p[2] == q[2] and chebyshev(p, q) <= CLUSTER_GAP for q in group):
                    group.append(p)
                    remaining.remove(p)
                    grew = True
        groups.append(group)
    return groups


def medoid(group):
    return min(group, key=lambda p: (sum(chebyshev(p, q) for q in group), p))


def choose_spots(spawn_lists, walkable, reachable, keep=None, keep_note=''):
    """Kill spots from spawn lists: (spots, source, dropped). keep, when given, is a further test every
    tile must pass, described by keep_note in the source."""
    points = []
    kept = []
    dropped = []
    for group in spawn_lists:
        good = [p for p in group['points'] if walkable is None or walkable.get(p, False)]
        # A spawn list that is mostly unwalkable is in display coordinates or on the wrong plane.
        # Its few walkable tiles could belong to some other place, so the whole list is dropped.
        if len(good) < RELIABLE_SHARE * len(group['points']):
            dropped.append('%s LocLine %r: %d of %d tiles walkable' % (
                group['page'], group['text'], len(good), len(group['points'])))
            continue
        # Shortest Path has no transport into some places. A route to a spawn there would end
        # somewhere unrelated, so those spawns are left out and the entrance is used instead.
        routable = [p for p in good if reachable is None or reachable.get(p, False)]
        if not routable:
            dropped.append('%s LocLine %r: Shortest Path cannot reach any of its %d walkable tiles' % (
                group['page'], group['text'], len(good)))
            continue
        if keep is not None:
            inside = [p for p in routable if keep(p, group)]
            if not inside:
                dropped.append('%s LocLine %r: none of its %d tiles %s' % (
                    group['page'], group['text'], len(routable), keep_note))
                continue
            routable = inside
        kept.append(group)
        points.extend(p for p in routable if p not in points)
    groups = sorted(clusters(points), key=lambda g: (-len(g), medoid(g)))
    spots = [list(medoid(g)) for g in groups[:MAX_SPOTS]]
    if not spots:
        return spots, None, dropped
    pages = OrderedDict()
    for group in kept:
        pages.setdefault(group['page'], []).append(group['text'])
    parts = ['%s (LocLine %s)' % (wiki_url(p), '; '.join("'%s'" % t for t in sorted(set(texts))))
             for p, texts in pages.items()]
    checks = [c for c, done in (('walkable', walkable), ('reachable', reachable)) if done is not None]
    source = ('OSRS Wiki spawn coordinates: ' + ', '.join(parts) + '. One spawn tile per cluster' + (
        ', ' + ' and '.join(checks) + ' in Shortest Path 1.20.7' if checks else '') + '.')
    if keep is not None:
        source += ' Every tile ' + keep_note + '.'
    return spots, source, dropped


def krystilia_places(curated, manifest, report, resolve_keys, keys_for, place_names, locations):
    """Places and spawn lists for each task Krystilia assigns, from the Location column of her wiki page,
    which lists only places within the Wilderness. Returns {(task id, key): place} in page order, each
    with the page's name for the place and the spawn lists of the task's monsters there."""
    spec = curated.get('krystilia')
    if not spec:
        return OrderedDict()
    text, final = page_text(manifest, spec['page'])
    if text is None:
        report.append('WARNING: no cached page for %s' % spec['page'])
        return OrderedDict()
    aliases = spec.get('aliases', {})
    monsters_by_task = {}
    for task in curated['tasks']:
        monsters_by_task.setdefault(task['taskId'], []).extend(task.get('monsters', []))
    places = OrderedDict()
    for monster, cell in krystilia_rows(text):
        task_id = spec['tasks'].get(monster)
        if task_id is None:
            report.append('KRYSTILIA: no task id for %r, skipped' % monster)
            continue
        task_places = []
        for place_text, link, place_links in split_places(cell):
            keys = aliases.get(place_text) or keys_for(link, place_text)
            for alias_key in keys:
                if alias_key not in locations:
                    locations[alias_key] = OrderedDict([('key', alias_key), ('label', place_text), ('wikiOnly', True),
                                                        ('tasks', OrderedDict())])
            key = keys[0]
            place_names.setdefault(key, set()).add(place_text)
            entry = places.setdefault((task_id, key), {'taskId': task_id, 'key': key, 'text': place_text,
                                                       'links': set(), 'groups': [], 'page': final})
            entry['links'].update(l for l in place_links if l != 'wilderness')
            task_places.append(entry)
        monsters = list(OrderedDict.fromkeys(monsters_by_task.get(task_id, []) + spec.get('monsters', {}).get(str(task_id), [])))
        for monster_page in monsters:
            page, page_final = page_text(manifest, monster_page)
            if page is None:
                report.append('WARNING: no cached page for %s' % monster_page)
                continue
            for (_s, _e, body) in find_templates(page, 'LocLine'):
                params = map_params(body)
                raw = params['named'].get('location', '')
                loc_text = clean_location_text(raw)
                raw_links = links(raw)
                if not params['coords']:
                    continue
                by_name = set(aliases.get(loc_text) or resolve_keys(raw_links[0] if raw_links else loc_text, loc_text))
                matches = [p for p in task_places if p['key'] in by_name]
                if not matches:
                    # Spawn lists name places like "Wilderness - south-east of the Demonic Ruins", so any
                    # link other than the Wilderness itself may name the place.
                    matches = [p for p in task_places if p['links'] & {l.lower() for l in raw_links}]
                if len(matches) != 1:
                    continue
                matches[0]['groups'].append({'page': page_final, 'text': loc_text,
                                             'points': [tuple(c) for c in params['coords']]})
    return places


def wilderness_point(p, key, whole_places):
    """Whether a tile is in the Wilderness: inside RuneLite's Wilderness areas, or in a place whose wiki
    page says all of it lies in the Wilderness."""
    return in_runelite_wilderness(p) or key in whole_places


def krystilia_rows(text):
    """(monster link, location cell) for each row of the Tasks table on Krystilia's wiki page. The table
    ends with an all-header total row, so its header is read from the lines before the first row."""
    for table in parse_tables(text):
        if not table['rows'] or not table['rows'][0]:
            continue
        start = text.find(table['rows'][0][0])
        head = text[max(0, text.rfind('{|', 0, start)):start]
        names = [re.sub(r'\[\[[^\]|]*\|([^\]]+)\]\]|\[\[([^\]]+)\]\]', lambda m: m.group(1) or m.group(2), h).strip().lower()
                 for h in re.findall(r'^!\s*(.+?)\s*$', head, re.M)]
        if 'location' not in names or 'monster' not in names:
            continue
        loc = names.index('location')
        for row in table['rows']:
            row_links = links(row[0])
            if len(row) == len(names) and row_links:
                yield row_links[0], row[loc]


def split_places(cell):
    """The places in one Location cell: (text, first link, every link), split at commas outside links and
    brackets. Notes in brackets, such as "(Bring a lockpick!)", are dropped from the text."""
    parts, depth, cur = [], 0, ''
    for ch in cell:
        if ch in '[({':
            depth += 1
        elif ch in '])}':
            depth = max(0, depth - 1)
        if ch in ',;' and depth == 0:
            parts.append(cur)
            cur = ''
        else:
            cur += ch
    parts.append(cur)
    out = []
    for part in parts:
        bare = re.sub(r"\(([^()]|\[\[[^\]]*\]\])*\)", '', part)
        text = clean_location_text(bare)
        if not text:
            continue
        text = text[0].upper() + text[1:]
        part_links = links(part)
        out.append((text, part_links[0] if part_links else text, [l.lower() for l in part_links]))
    return out


def monster_table_places(tables, task, manifest, excluded_rows):
    """(text, link) for each place a guide's monster table lists under one of the task's monsters."""
    wanted = set()
    for monster in task.get('monsters', []):
        wanted.add(monster.lower())
        entry = manifest.get(monster)
        if entry:
            wanted.add(entry['final'].lower())
    for table in tables:
        header = [re.sub(r'<[^>]+>|\[\[|\]\]', '', h).strip().lower() for h in table['header']]
        if not header or header[0] != 'monster':
            continue
        loc_col = next((i for i, h in enumerate(header) if h.startswith('location')), None)
        if loc_col is None:
            continue
        for row in table['rows']:
            if len(row) != len(header):
                continue
            monster_links = links(row[0])
            if not monster_links or monster_links[0].lower() not in wanted:
                continue
            for line in row[loc_col].split('\n'):
                line = line.strip().lstrip('*').strip()
                line_links = links(line)
                # "Various other locations. See [[Giant rat]] ..." points at a monster page, not a place.
                if not line_links or re.match(r'various\b', line, re.I) or 'See [[' in line:
                    continue
                text = clean_location_text(line)
                if text and not any(p.search(text) for p in excluded_rows):
                    yield text, line_links[0]


def comparable(text):
    return re.sub(r'\s+', ' ', text.replace('’', "'")).strip().lower()


def cached_page(manifest, title):
    """A cached page by the title asked for, or by the title it is cached under."""
    text, final = page_text(manifest, title)
    if text is None and title in {entry['final'] for entry in manifest.values()}:
        path = os.path.join(CACHE, re.sub(r'[^A-Za-z0-9_.-]+', '_', title) + '.txt')
        if os.path.exists(path):
            return strip_comments(open(path, encoding='utf-8').read()), title
    return text, final


def access_source(sources, manifest, plain, where, report):
    """The source line of an access entry, with every quote and infobox field checked against its cached page.
    None when one cannot be found there, so a page that changed never locks a place on old words."""
    parts = []
    for src in sources:
        text, final = cached_page(manifest, src.get('page', ''))
        if text is None:
            report.append('ACCESS DROPPED %s: %s is not cached' % (where, src.get('page')))
            return None
        url = wiki_url(final)
        if 'quote' in src:
            if final not in plain:
                plain[final] = comparable(plain_text(text))
            if comparable(src['quote']) not in plain[final]:
                report.append('ACCESS DROPPED %s: quote not found on %s: %s' % (where, final, src['quote']))
                return None
            parts.append("OSRS Wiki %s: '%s'" % (url, src['quote']))
        elif 'field' in src:
            value = infobox_field(text, src['field'])
            if value is None or comparable(src.get('value', '')) not in comparable(value):
                report.append('ACCESS DROPPED %s: infobox %s on %s is %r' % (where, src['field'], final, value))
                return None
            parts.append('OSRS Wiki %s infobox %s: %s.' % (url, src['field'], src['value']))
        else:
            report.append('ACCESS DROPPED %s: a source names neither a quote nor a field' % where)
            return None
    if not parts:
        report.append('ACCESS DROPPED %s: no source' % where)
        return None
    return ' '.join(parts)


def access_entry(spec, manifest, plain, where, report):
    """The access object for one place or one task at a place, or None when it needs nothing or its source fails."""
    entry = OrderedDict((field, spec[field]) for field in ACCESS_FIELDS if spec.get(field))
    if not entry:
        return None
    source = access_source(spec.get('sources', []), manifest, plain, where, report)
    if source is None:
        return None
    entry['source'] = source
    return entry


def access_by_place(manifest, locations, shown, report):
    """Access requirements from access.json: {key: access} for places and {(key, task id): access} for tasks whose
    monsters lie behind a further gate. Also reports how many of the places in the output (shown) need something,
    were checked and need nothing, or are not settled."""
    if not os.path.exists(ACCESS):
        report.append('NOTE: access.json missing, no place carries access requirements.')
        return {}, {}
    spec = json.load(open(ACCESS, encoding='utf-8'), object_pairs_hook=OrderedDict)
    plain = {}
    places, tasks = {}, {}
    for key, place in spec.get('locations', {}).items():
        if key not in locations:
            report.append('WARNING: access for unknown location %s' % key)
            continue
        entry = access_entry(place, manifest, plain, key, report)
        if entry:
            places[key] = entry
        for task_id, task in place.get('tasks', {}).items():
            if int(task_id) not in locations[key]['tasks']:
                report.append('WARNING: access for task %s, which %s does not list' % (task_id, key))
                continue
            entry = access_entry(task, manifest, plain, '%s task %s' % (key, task_id), report)
            if entry:
                tasks[(key, int(task_id))] = entry
    open_places = set()
    for key, src in spec.get('open', {}).items():
        if key not in locations:
            report.append('WARNING: open access for unknown location %s' % key)
        elif key in spec.get('locations', {}):
            report.append('WARNING: %s is both open and gated in access.json' % key)
        elif access_source([src], manifest, plain, key + ' (open)', report) is not None:
            open_places.add(key)
    def locks(entry):
        return any(entry.get(field) for field in ACCESS_FIELDS if field != 'note')

    gated = set(places) | {key for key, _ in tasks}
    place_locks = {key for key, entry in places.items() if locks(entry)}
    locked = place_locks | {key for (key, _), entry in tasks.items() if locks(entry)}
    unchecked = [key for key in locations if key in shown and key not in gated and key not in open_places]
    report.append('Access: %d places can lock (%d only for some tasks), %d carry a note only, %d need nothing, '
                  '%d not settled.' % (len(locked), len(locked - place_locks), len(gated - locked), len(open_places),
                                       len(unchecked)))
    report.append('Access not settled: ' + (', '.join(unchecked) or 'none'))
    return places, tasks


def main():
    curated = json.load(open(os.path.join(HERE, 'curated.json'), encoding='utf-8'), object_pairs_hook=OrderedDict)
    manifest = json.load(open(os.path.join(CACHE, 'manifest.json'), encoding='utf-8'))
    dump = json.load(open(DUMP, encoding='utf-8'))
    rl = load_runelite_points()
    report = []

    # Locations the game knows as Slayer areas, keyed by curated stable keys.
    locations = OrderedDict()
    area_rows = {a['name']: a for a in dump['slayerAreas']}
    for row_name, spec in curated['areaKeys'].items():
        area = area_rows.get(row_name)
        if area is None:
            report.append('WARNING: curated area row %s is not in the cache dump' % row_name)
            continue
        locations[spec['key']] = OrderedDict([('key', spec['key']), ('label', spec['label']),
                                              ('areaId', area['areaId']), ('row', row_name), ('tasks', OrderedDict())])
    for row_name in area_rows:
        if row_name not in curated['areaKeys']:
            report.append('WARNING: cache dump area %s has no curated key' % row_name)
    for spec in curated.get('extraLocations', []):
        locations[spec['key']] = OrderedDict([('key', spec['key']), ('label', spec['label']), ('wikiOnly', True),
                                              ('tasks', OrderedDict())])

    aliases = {name: keys for name, keys in curated['aliases'].items()}
    for key, loc in locations.items():
        aliases.setdefault(loc['label'], [key])
    excluded = [re.compile(p, re.I) for p in curated['excludeLocationText']]
    # Every name a place was found under, for matching the dossier's best location text.
    place_names = {}
    for name, keys in aliases.items():
        for key in keys:
            place_names.setdefault(key, set()).add(name)

    def resolve_keys(link, text):
        """Location keys for a wiki place name, or [] when the name is excluded. Creates nothing."""
        if any(p.search(text) for p in excluded):
            return []
        if text in aliases:
            return aliases[text]
        if link in aliases:
            return aliases[link]
        return [slug(text if text else link)]

    def keys_for(link, text):
        keys = resolve_keys(link, text)
        for key in keys:
            if key not in locations:
                locations[key] = OrderedDict([('key', key), ('label', text or link), ('wikiOnly', True),
                                              ('tasks', OrderedDict())])
            place_names.setdefault(key, set()).update(n for n in (text, link) if n)
        return keys

    def task_slot(key, task):
        slots = locations[key]['tasks']
        if task['taskId'] not in slots:
            slots[task['taskId']] = {'task': task['task'], 'groups': [], 'rows': [], 'order': len(slots)}
        return slots[task['taskId']]

    dossier = dossier_best_locations()
    task_order = OrderedDict()
    guide_only_tasks = set()
    for task in curated['tasks']:
        order = task_order.setdefault(task['taskId'], [])
        excluded_rows = [re.compile(p, re.I) for p in task.get('excludeRows', [])]
        # placesFromGuide: list only the places the task guide names. Spawn lists then add kill spots to
        # those places and nothing else. Meant for tasks whose monsters spawn all over the map.
        guide_only = task.get('placesFromGuide', False)
        if guide_only:
            guide_only_tasks.add(task['taskId'])
        guide_links = OrderedDict()

        def guide_place(key, link):
            guide_links.setdefault(key, set()).add(link)
            if key not in order:
                order.append(key)

        # Facts from the task guide's location comparison table.
        if task.get('page'):
            text, final = page_text(manifest, task['page'])
            if text is None:
                report.append('WARNING: no cached page for %s' % task['page'])
            else:
                tables = parse_tables(text)
                for table in tables:
                    header = [re.sub(r'<[^>]+>|\[\[|\]\]', '', h).strip().lower() for h in table['header']]
                    if not any('multicombat' in h for h in header):
                        continue
                    multi_col = next(i for i, h in enumerate(header) if 'multicombat' in h)
                    cannon_col = next((i for i, h in enumerate(header) if 'cannon' in h), None)
                    for row in table['rows']:
                        if len(row) != len(header):
                            continue
                        row_text = clean_location_text(row[0])
                        if any(p.search(row_text) for p in excluded_rows):
                            continue
                        row_links = links(row[0])
                        row_link = row_links[0] if row_links else row_text
                        for key in keys_for(row_link, row_text):
                            slot = task_slot(key, task)
                            slot['rows'].append({'text': row_text, 'multi': yesno(row[multi_col]),
                                                 'cannon': yesno(row[cannon_col]) if cannon_col is not None else None,
                                                 'page': final})
                            guide_place(key, row_link)
                # Without a comparison table, the places the guide's monster table lists for each monster.
                if guide_only and not guide_links:
                    for place_text, place_link in monster_table_places(tables, task, manifest, excluded_rows):
                        for key in keys_for(place_link, place_text):
                            task_slot(key, task)
                            guide_place(key, place_link)
        if guide_only and not guide_links:
            report.append('WARNING: %s lists places from its guide, but the guide names none' % task['task'])
        # Places a page names only in its text, such as the Canifis werewolves. Each carries its source.
        for place in task.get('places', []):
            for key in keys_for(place.get('link') or place['text'], place['text']):
                slot = task_slot(key, task)
                slot['note'] = place['note']
                slot['noteSource'] = place['noteSource']
                guide_place(key, place.get('link') or place['text'])
        outside = OrderedDict()
        # Spawn coordinates from the monsters' LocLine templates.
        for monster in task.get('monsters', []):
            text, final = page_text(manifest, monster)
            if text is None:
                report.append('WARNING: no cached page for %s' % monster)
                continue
            for (_s, _e, body) in find_templates(text, 'LocLine'):
                params = map_params(body)
                raw = params['named'].get('location', '')
                loc_text = clean_location_text(raw)
                if any(p.search(loc_text) for p in excluded_rows):
                    continue
                loc_links = links(raw)
                loc_link = loc_links[0] if loc_links else loc_text
                group = {'page': final, 'text': loc_text, 'points': [tuple(c) for c in params['coords']]}
                if guide_only:
                    keys = [k for k in resolve_keys(loc_link, loc_text) if k in guide_links]
                    if not keys:
                        # A spawn list for a guide place under another name, matched by its wiki link.
                        by_link = [k for k, names in guide_links.items() if loc_link in names]
                        keys = by_link if len(by_link) == 1 else []
                    if not keys:
                        for key in resolve_keys(loc_link, loc_text):
                            spot = outside.setdefault(key, {'link': loc_link, 'text': loc_text, 'groups': []})
                            if group['points']:
                                spot['groups'].append(group)
                        continue
                else:
                    keys = keys_for(loc_link, loc_text)
                for key in keys:
                    slot = task_slot(key, task)
                    if group['points']:
                        slot['groups'].append(group)
                    if key not in order:
                        order.append(key)
        if outside:
            # The place the dossier recommends stays listed even when the guide leaves it out.
            names = dict(place_names)
            for key, spot in outside.items():
                names[key] = place_names.get(key, set()) | {spot['text'], spot['link']}
            named = dossier_place(dossier_best(dossier, task), list(guide_links) + list(outside),
                                  curated['dossierPrefixes'], names, curated.get('dossierPlaces', {}).get(task['task']))
            if named in outside:
                spot = outside.pop(named)
                for key in keys_for(spot['link'], spot['text']):
                    task_slot(key, task)['groups'].extend(spot['groups'])
                    if key not in order:
                        order.append(key)
                report.append('GUIDE ONLY %s: kept %s, the place the dossier names' % (task['task'], named))
            report.append('GUIDE ONLY %s: left out %d places that only spawn lists name' % (task['task'], len(outside)))

    # Krystilia's tasks only count in the Wilderness. Her wiki page lists where each one can be done.
    krystilia = krystilia_places(curated, manifest, report, resolve_keys, keys_for, place_names, locations)

    # Results of check_walkable.sh against the Shortest Path jar, when available.
    os.makedirs(WORK, exist_ok=True)
    candidates = sorted({p for loc in locations.values() for t in loc['tasks'].values()
                         for g in t['groups'] for p in g['points']}
                        | {p for k in krystilia.values() for g in k['groups'] for p in g['points']})
    walkable = load_check(os.path.join(WORK, 'walkable.tsv'), candidates, report, 'walkable')
    reachable = load_check(os.path.join(WORK, 'reachable.tsv'), candidates, report, 'reachable')

    # Kill spots and facts per task and location.
    facts = curated.get('taskFacts', {})
    for key, loc in locations.items():
        for task_id, slot in loc['tasks'].items():
            slot['spots'], slot['spotsSource'], slot['dropped'] = choose_spots(slot['groups'], walkable, reachable)
            multi = {r['multi'] for r in slot['rows']}
            cannon = {r['cannon'] for r in slot['rows'] if r['cannon']}
            if slot['rows']:
                source = 'OSRS Wiki %s, location comparison table, rows %s' % (
                    wiki_url(slot['rows'][0]['page']), ', '.join("'%s'" % r['text'] for r in slot['rows']))
                if multi <= {'yes', 'no', 'partial'} and multi:
                    slot['combat'] = 'multi' if multi == {'yes'} else 'single' if multi == {'no'} else 'mixed'
                    slot['combatSource'] = source
                if cannon in ({'yes'}, {'no'}):
                    slot['cannon'] = cannon.pop()
                    slot['cannonSource'] = source
                elif cannon:
                    slot['cannonConflict'] = sorted(cannon)
            override = facts.get('%s|%d' % (key, task_id))
            if override:
                for field, value in override.items():
                    if value is None:
                        slot.pop(field, None)
                    else:
                        slot[field] = value

    # Entrances and place-wide facts.
    for key, spec in curated['entrances'].items():
        loc = locations.get(key)
        if loc is None:
            report.append('WARNING: entrance for unknown location %s' % key)
            continue
        if 'runelite' in spec:
            cls, name, field = spec['runelite'].split('.') + ['Location'] if spec['runelite'].count('.') == 1 \
                else spec['runelite'].split('.')
            found = rl.get((cls, name, field))
            if not found:
                report.append('WARNING: RuneLite point %s not found' % spec['runelite'])
                continue
            loc['entrance'] = found['point']
            loc['entranceSource'] = ('RuneLite 1.13.1 world map data, %s.%s%s (%s)' % (
                cls, name, '' if field == 'Location' else ' ' + field.lower(), found['tooltip'])
                + ('. ' + spec['source'] if spec.get('source') else ''))
        elif 'spotsOfTask' in spec:
            slot = loc['tasks'].get(spec['spotsOfTask'])
            if not slot or not slot.get('spots'):
                report.append('WARNING: %s takes its entrance from task %s, which has no spots' % (key, spec['spotsOfTask']))
                continue
            loc['entrance'] = slot['spots'][0]
            loc['entranceSource'] = 'First kill spot of %s here. %s' % (slot['task'], slot['spotsSource'])
        else:
            loc['entrance'] = spec['point']
            loc['entranceSource'] = spec['source']
        if spec.get('label'):
            loc['entranceLabel'] = spec['label']
    for key, spec in curated.get('locationFacts', {}).items():
        if key in locations:
            locations[key].update(spec)
        else:
            report.append('WARNING: facts for unknown location %s' % key)

    # Automatic entrances for wiki-only locations whose name matches a RuneLite dungeon icon.
    dungeons = {}
    for (cls, name, field), value in rl.items():
        if cls == 'DungeonLocation' and field == 'Location':
            dungeons.setdefault(value['tooltip'].lower(), (name, value))
    for key, loc in locations.items():
        if 'entrance' in loc or not loc.get('wikiOnly'):
            continue
        match = dungeons.get(loc['label'].lower())
        if match:
            loc['entrance'] = match[1]['point']
            loc['entranceSource'] = 'RuneLite 1.13.1 world map data, DungeonLocation.%s (%s), matched by name' % (
                match[0], match[1]['tooltip'])

    # Candidate tiles for the next check_walkable.sh run: every spawn and the tiles around each entrance.
    entrance_tiles = set()
    for loc in locations.values():
        if loc.get('entrance'):
            x, y, z = loc['entrance']
            entrance_tiles.update((x + dx, y + dy, z) for dx in range(-ENTRANCE_REACH, ENTRANCE_REACH + 1)
                                  for dy in range(-ENTRANCE_REACH, ENTRANCE_REACH + 1))
    with open(os.path.join(WORK, 'candidates.tsv'), 'w', encoding='utf-8', newline='\n') as f:
        for p in sorted(set(candidates) | entrance_tiles):
            f.write('%d\t%d\t%d\n' % p)
    if reachable is not None:
        for loc in locations.values():
            if loc.get('entrance'):
                x, y, z = loc['entrance']
                near = [reachable.get((x + dx, y + dy, z)) for dx in range(-ENTRANCE_REACH, ENTRANCE_REACH + 1)
                        for dy in range(-ENTRANCE_REACH, ENTRANCE_REACH + 1)]
                if None in near:
                    report.append('NOTE: entrance of %s not checked yet. Run check_walkable.sh again.' % loc['key'])
                elif not any(near):
                    loc['entranceUnroutable'] = True
                    report.append('UNREACHABLE ENTRANCE %s %s: Shortest Path cannot get within %d tiles' % (
                        loc['key'], loc['entrance'], ENTRANCE_REACH))

    wilderness_lists = wilderness_task_lists(curated, dump, krystilia, locations, place_names, dossier, walkable,
                                             reachable, report)
    wilderness_keys = {p['key'] for w in wilderness_lists for p in w['places']}

    # What each place, and each task at a place, needs to get in.
    shown = {key for key, loc in locations.items()
             if loc['tasks'] or 'areaId' in loc or 'entrance' in loc or key in wilderness_keys}
    access, task_access = access_by_place(manifest, locations, shown, report)

    # Assemble the output.
    out_locations = []
    for key, loc in locations.items():
        if key not in shown:
            continue
        entry = OrderedDict([('key', key), ('label', loc['label'])])
        for field in ('areaId', 'row', 'entrance', 'entranceLabel', 'entranceSource', 'cannon', 'cannonNote',
                      'cannonSource', 'combat', 'combatSource', 'note', 'noteSource'):
            if loc.get(field) is not None:
                entry[field] = loc[field]
            if field == 'entranceSource' and loc.get('entranceUnroutable'):
                entry['entranceRoutable'] = False
                entry['entranceRoutableSource'] = ('No tile within %d tiles of the entrance is reachable in Shortest Path '
                                                   '1.20.7 (tools/wiki-extract/ReachabilityCheck.java)' % ENTRANCE_REACH)
        if key in access:
            entry['access'] = access[key]
        tasks = []
        for task_id, slot in sorted(loc['tasks'].items(), key=lambda kv: kv[1]['order']):
            t = OrderedDict([('taskId', task_id), ('task', slot['task'])])
            for field in ('spots', 'spotsSource', 'cannon', 'cannonNote', 'cannonSource', 'combat', 'combatSource',
                          'note', 'noteSource'):
                if slot.get(field):
                    t[field] = slot[field]
            if (key, task_id) in task_access:
                t['access'] = task_access[(key, task_id)]
            tasks.append(t)
        if tasks:
            entry['tasks'] = tasks
        out_locations.append(entry)

    out_keys = {e['key'] for e in out_locations}
    task_lists = []
    for task in curated['tasks']:
        keys = [k for k in task_order.get(task['taskId'], []) if k in out_keys]
        if not keys:
            report.append('WARNING: no places found for %s' % task['task'])
            continue
        slots = {k: locations[k]['tasks'].get(task['taskId'], {}) for k in keys}
        rec, why = recommend(dossier_best(dossier, task), keys, curated['dossierPrefixes'], place_names,
                             lambda k: bool(locations[k].get('entrance') or slots[k].get('spots')),
                             curated.get('dossierPlaces', {}).get(task['task']))
        keys.remove(rec)
        keys.insert(0, rec)
        entry = OrderedDict([('taskId', task['taskId']), ('task', task['task']), ('locations', keys),
                             ('recommended', rec)])
        entry['source'] = ('Places the OSRS Wiki task guide names, in wiki order. Kill spots from LocLine spawn tables. '
                           if task['taskId'] in guide_only_tasks else
                           'Places from the OSRS Wiki task guide and LocLine spawn tables, in wiki order. ') \
            + 'Recommended place: ' + why + '.'
        task_lists.append(entry)

    document = OrderedDict([
        ('about', curated['about']),
        ('locations', out_locations),
        ('taskLocations', task_lists),
        ('wildernessTaskLocations', wilderness_lists),
    ])
    text = json.dumps(document, indent=1, ensure_ascii=False)
    # One point per line: [x, y, plane] stays readable and easy to edit by hand.
    text = re.sub(r'\[\s+(-?\d+),\s+(-?\d+),\s+(-?\d+)\s+\]', r'[\1, \2, \3]', text)
    with open(OUT_JSON, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text + '\n')

    write_report(report, locations, dump, task_lists, wilderness_lists)


def task_names(dump):
    """Task name per task id, from the masters' pools in the cache dump."""
    ids = {r['row']: r['values']['COL_ID'][0] for r in dump['tables']['SlayerTask']['rows'] if r['values'].get('COL_ID')}
    names = {}
    for master in dump['masters']:
        for t in master['tasks']:
            if t['taskRow'] in ids:
                names.setdefault(ids[t['taskRow']], t['task'])
    return names


def wilderness_task_lists(curated, dump, krystilia, locations, place_names, dossier, walkable, reachable, report):
    """Krystilia's task lists. Every point in them is checked to lie in the Wilderness: kill spots inside
    RuneLite's Wilderness areas or in a place whose wiki page says all of it is in the Wilderness, and the
    marker only when the place's entrance is inside RuneLite's Wilderness areas."""
    spec = curated.get('krystilia')
    if not spec:
        return []
    whole = spec.get('wholePlaces', {})
    names = task_names(dump)
    page = wiki_url(spec['page'])
    by_task = OrderedDict()
    for (task_id, key), entry in krystilia.items():
        by_task.setdefault(task_id, []).append(entry)
    out = []
    for task_id, entries in by_task.items():
        places = []
        for entry in entries:
            key = entry['key']
            loc = locations[key]
            note = ('lies in the Wilderness, as the OSRS Wiki says of all of this place: ' + whole[key]) if key in whole \
                else 'lies in the ' + RUNELITE_WILDERNESS_SOURCE
            spots, spots_source, dropped = choose_spots(entry['groups'], walkable, reachable,
                                                        lambda p, g, k=key: wilderness_point(p, k, whole), note)
            for d in dropped:
                report.append('DROPPED for Krystilia %s at %s: %s' % (names.get(task_id, task_id), key, d))
            place = OrderedDict([('key', key), ('label', entry['text'])])
            # A place whose own points reach outside the Wilderness is only partly in it.
            own = ([loc['entrance']] if loc.get('entrance') else []) + [
                s for slot in loc['tasks'].values() for s in slot.get('spots', [])]
            if any(not wilderness_point(p, key, whole) for p in own):
                place['label'] = entry['text'] + ' (Wilderness part)'
            entrance = loc.get('entrance')
            if entrance and in_runelite_wilderness(entrance):
                place['entrance'] = entrance
                if loc.get('entranceLabel'):
                    place['entranceLabel'] = loc['entranceLabel']
                place['entranceSource'] = loc['entranceSource'] + ' The point lies in the ' + RUNELITE_WILDERNESS_SOURCE + '.'
                if loc.get('entranceUnroutable'):
                    place['entranceRoutable'] = False
                    place['entranceRoutableSource'] = ('No tile within %d tiles of the entrance is reachable in Shortest '
                                                       'Path 1.20.7 (tools/wiki-extract/ReachabilityCheck.java)' % ENTRANCE_REACH)
            elif entrance:
                report.append('KRYSTILIA %s at %s: entrance %s is outside the Wilderness, no marker there' % (
                    names.get(task_id, task_id), key, entrance))
            if spots:
                place['spots'] = spots
                place['spotsSource'] = spots_source
            places.append(place)
        keys = [p['key'] for p in places]
        task = {'task': names.get(task_id, str(task_id)), 'dossierName': spec.get('dossierNames', {}).get(str(task_id))}
        rec, why = recommend(dossier_best(dossier, task), keys, curated['dossierPrefixes'], place_names,
                             lambda k: any(p['key'] == k and (p.get('entrance') or p.get('spots')) for p in places),
                             None, skip_krystilia=False)
        places.sort(key=lambda p: p['key'] != rec)
        out.append(OrderedDict([
            ('taskId', task_id), ('task', task['task']), ('places', places), ('recommended', rec),
            ('source', 'OSRS Wiki %s Tasks table, Location column, which lists only places within the Wilderness. '
                       'Recommended place: %s.' % (page, why))]))
    return out


def norm(s):
    return ''.join(c for c in s.lower() if c.isalnum())


def dossier_best_locations():
    """The dossier's best location text per normalised task name. Read only."""
    dossier = {}
    for name in ('slayer_tasks.json', 'slayer_tasks_supplement.json'):
        path = os.path.join(PROJECT, 'src', 'main', 'resources', name)
        for entry in json.load(open(path, encoding='utf-8-sig')):
            dossier.setdefault(norm(entry['task']), entry.get('bestLocation') or '')
    return dossier


def dossier_best(dossier, task):
    wanted = norm(task.get('dossierName') or task['task'])
    best = dossier.get(wanted)
    if best is None:
        best = next((v for k, v in dossier.items() if k.startswith(wanted)), None)
    return best or None


def dossier_place(best, keys, prefixes, names, pinned=None, skip_krystilia=True):
    """The listed place the dossier's best location names, or None. A curated dossierPlaces entry comes
    first, then the same prefixes as TaskDestination.forTask, then the listed place the text mentions
    first. Advice meant for Krystilia is skipped unless the list is hers: her tasks only count in the
    Wilderness and get their own place lists."""
    if pinned in keys:
        return pinned
    if not best:
        return None
    lower = best.lower()
    for prefix, key in prefixes:
        if lower.startswith(prefix):
            if key in keys:
                return key
            break
    clauses = re.split(r'(?<=[;.])\s+', lower)
    text = ' '.join(c for c in clauses if not skip_krystilia or 'krystilia' not in c)
    return first_mention(text, keys, names)


def recommend(best, keys, prefixes, names, has_point, pinned=None, skip_krystilia=True):
    """The place to recommend and why: the place the dossier's best location names, else the first listed
    place with a verified point, else the first listed place."""
    named = dossier_place(best, keys, prefixes, names, pinned, skip_krystilia)
    if named:
        return named, 'the place the dossier best location names'
    for key in keys:
        if has_point(key):
            return key, 'the first listed place with a verified point'
    return keys[0], 'the first listed place'


def first_mention(text, keys, names):
    """The listed place whose name appears earliest in the text. At the same spot the longer name wins."""
    found = None
    for key in keys:
        for name in names.get(key, ()):
            name = name.lower().strip()
            if len(name) < 4:
                continue
            m = re.search(r'(?<![a-z0-9])' + re.escape(name) + r'(?![a-z0-9])', text)
            if m and (found is None or (m.start(), -len(name)) < found[0]):
                found = ((m.start(), -len(name)), key)
    return found[1] if found else None


MASTER_NAMES = OrderedDict([(1, 'Turael'), (9, 'Spria'), (2, 'Mazchna'), (3, 'Vannaka'), (4, 'Chaeldar'),
                            (6, 'Nieve'), (5, 'Duradel'), (8, 'Konar'), (7, 'Krystilia'), (10, 'Mortimer')])


def coverage_lines(dump, task_lists):
    """Per master: pool tasks with two or more places, with one place and with none."""
    task_ids = {r['row']: r['values']['COL_ID'][0] for r in dump['tables']['SlayerTask']['rows']
                if r['values'].get('COL_ID')}
    places = {t['taskId']: len(t['locations']) for t in task_lists}
    masters = {m['masterId']: m for m in dump['masters']}
    lines = []
    for master_id, name in MASTER_NAMES.items():
        pool = masters.get(master_id, {}).get('tasks', [])
        counts = [(places.get(task_ids.get(t['taskRow']), 0), t['task']) for t in pool]
        none = [n for c, n in counts if c == 0]
        lines.append('Coverage %s: %d tasks. Two or more places: %d. One place: %d. None: %d%s' % (
            name, len(counts), sum(1 for c, _ in counts if c >= 2), sum(1 for c, _ in counts if c == 1), len(none),
            ' (' + ', '.join(none) + ')' if none else ''))
    return lines


def wilderness_coverage_lines(dump, wilderness_lists):
    """Krystilia's pool: tasks with Wilderness places, with a marked or routable one, and with none."""
    task_ids = {r['row']: r['values']['COL_ID'][0] for r in dump['tables']['SlayerTask']['rows']
                if r['values'].get('COL_ID')}
    pool = next((m['tasks'] for m in dump['masters'] if m['masterId'] == 7), [])
    lists = {w['taskId']: w for w in wilderness_lists}
    two, one, pointed, none = 0, 0, 0, []
    for t in pool:
        w = lists.get(task_ids.get(t['taskRow']))
        if not w:
            none.append(t['task'])
            continue
        two += len(w['places']) >= 2
        one += len(w['places']) == 1
        pointed += any(p.get('entrance') or p.get('spots') for p in w['places'])
    return ['Coverage Krystilia, Wilderness places only: %d tasks. Two or more places: %d. One place: %d. '
            'With a marker or route: %d. None: %d%s' % (len(pool), two, one, pointed, len(none),
                                                        ' (' + ', '.join(none) + ')' if none else '')]


def write_report(report, locations, dump, task_lists, wilderness_lists):
    by_area = {loc['areaId']: loc for loc in locations.values() if 'areaId' in loc}
    pairs = 0
    with_spots = 0
    lines = []
    missing_spots = []
    for task in dump['konarTaskAreas']['tasks']:
        for area in task['areas']:
            pairs += 1
            loc = by_area.get(area['areaId'])
            slot = loc['tasks'].get(task['taskId']) if loc else None
            if slot and slot.get('spots'):
                with_spots += 1
            else:
                missing_spots.append('%s at %s' % (task['task']['label'], area['label']))
    konar_areas = {a['areaId'] for t in dump['konarTaskAreas']['tasks'] for a in t['areas']}
    no_entrance = sorted(by_area[a]['label'] for a in konar_areas if a in by_area and 'entrance' not in by_area[a])
    lines.append('Konar areas: %d. With a verified entrance: %d.' % (len(konar_areas), len(konar_areas) - len(no_entrance)))
    lines.append('Konar areas without an entrance: ' + (', '.join(no_entrance) or 'none'))
    lines.append('Konar (task, area) pairs: %d. With kill spots: %d.' % (pairs, with_spots))
    lines.append('Pairs without kill spots: ' + ', '.join(missing_spots))
    lines.append('')
    lines.extend(coverage_lines(dump, task_lists))
    lines.extend(wilderness_coverage_lines(dump, wilderness_lists))
    lines.append('')
    for loc in locations.values():
        for task_id, slot in loc['tasks'].items():
            if slot.get('cannonConflict'):
                lines.append('CANNON CONFLICT %s / %s: %s' % (loc['key'], slot['task'],
                                                               ', '.join("'%s'=%s" % (r['text'], r['cannon']) for r in slot['rows'])))
            for dropped in slot.get('dropped', []):
                lines.append('DROPPED for %s at %s: %s' % (slot['task'], loc['key'], dropped))
    lines.extend(report)
    with open(REPORT, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')
    print('\n'.join(lines[:4]))


if __name__ == '__main__':
    main()
