#!/usr/bin/env python3
"""Writes the dossier's elementalWeakness fields from the monsters' wiki infoboxes.

Reads fight_monsters.json, which names the wiki monster pages of every dossier entry, and the cached
wikitext of those pages (fetch_pages.py fetches them). Writes elementalWeakness into
src/main/resources/slayer_tasks.json and slayer_tasks_supplement.json, right after weakness, and leaves
everything else in both files as it is. A second run changes nothing.
"""
import json
import os
import re
import sys

import wikiparse
from fetch_pages import MANIFEST, cache_file

HERE = os.path.dirname(os.path.abspath(__file__))
RESOURCES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources')
DOSSIERS = ['slayer_tasks.json', 'slayer_tasks_supplement.json']
ELEMENTS = ('Air', 'Water', 'Earth', 'Fire')
# A version whose weakness fields are left blank on the wiki: not known, so it is left out.
UNKNOWN = 'unknown'


def params(body):
    """The named parameters of a template, keys in lower case."""
    _name, parts = wikiparse.split_params(body)
    named = {}
    for part in parts:
        if '=' in part:
            key, value = part.split('=', 1)
            named[key.strip().lower()] = value.strip()
    return named


def infoboxes(text):
    """(tab, fields) for each Infobox Monster on a page. tab is the Multi Infobox tab it is shown under, or None."""
    text = wikiparse.strip_comments(text)
    found = []
    outside = text
    for start, end, body in wikiparse.find_templates(text, 'Multi Infobox'):
        tabs = params(body)
        for key, value in tabs.items():
            item = re.match(r'item(\d+)$', key)
            if item:
                tab = tabs.get('text' + item.group(1))
                found.extend((tab, params(box)) for _s, _e, box in wikiparse.find_templates(value, 'Infobox Monster'))
        outside = outside.replace(body, '')
    found.extend((None, params(box)) for _s, _e, box in wikiparse.find_templates(outside, 'Infobox Monster'))
    return found


def versions(tab, fields):
    """(names, weakness) for each version of one infobox. names holds its tab and its version name where it has
    them, and weakness is (element, percent), None or UNKNOWN."""
    numbers = sorted({int(m.group(1)) for m in (re.match(r'version(\d+)$', k) for k in fields) if m}) or [None]
    result = []
    for n in numbers:
        def field(name):
            return fields.get(name + str(n), fields.get(name)) if n is not None else fields.get(name)
        names = [part for part in (tab, fields.get('version%d' % n) if n is not None else None) if part]
        result.append((names, weakness(field('elementalweaknesstype'), field('elementalweaknesspercent'))))
    return result


def weakness(kind, percent):
    """(element, percent) from the infobox fields, None when it has no weakness, UNKNOWN when they are blank."""
    if kind is None or kind.strip().lower() == 'none':
        return None
    if not kind.strip():
        return UNKNOWN
    element = kind.strip().capitalize()
    if element not in ELEMENTS or not re.match(r'\d+$', (percent or '').strip()):
        raise ValueError('cannot read the weakness %r %r' % (kind, percent))
    return element, int(percent)


def page_versions(title, manifest):
    entry = manifest.get(title)
    path = cache_file(entry['final'] if entry else title)
    if not os.path.exists(path):
        raise SystemExit('not in the cache, run fetch_pages.py first: ' + title)
    found = [v for tab, fields in infoboxes(open(path, encoding='utf-8').read()) for v in versions(tab, fields)]
    if not found:
        raise SystemExit('no monster infobox on ' + title)
    return found


def selects(names, selector):
    """Whether a version named by its tab or version name is the one a selector names, such as "Zaros" or "Lvl 29"."""
    return any(name == selector or name.startswith(selector + ',') or name.startswith(selector + ' ') for name in names)


def kinds(monsters, manifest, skipped):
    """(label, weaknesses) for each kind of monster. "Page#Name" takes the versions with that name. A plain page
    takes its versions that no "Page#Name" of the same task takes."""
    claimed = {}
    for pages in monsters.values():
        for page in pages:
            if '#' in page:
                title, selector = page.split('#', 1)
                claimed.setdefault(title, []).append(selector)
    result = []
    for label, pages in monsters.items():
        values = []
        for page in pages:
            title, _, selector = page.partition('#')
            for names, value in page_versions(title, manifest):
                taken = any(selects(names, s) for s in claimed.get(title, []))
                if (selects(names, selector) if selector else not taken):
                    if value == UNKNOWN:
                        skipped.append(title + ' ' + ', '.join(names))
                    else:
                        values.append(value)
        if not values:
            raise SystemExit('no version of %s left for %r' % (pages, label))
        result.append((label, values))
    return result


def span(percents):
    low, high = min(percents), max(percents)
    return '%d%%' % low if low == high else '%d to %d%%' % (low, high)


def elemental_weakness(monsters, manifest, skipped):
    """The field: Water 40%, Water 40 to 50%, Fire 40% (cave goblins) when only some kinds have it, Fire 50% (ice
    trolls), Earth 50% (mountain trolls) when the element differs, "some" before a kind only part of whose versions
    have it, or empty when none has one."""
    found = kinds(monsters, manifest, skipped)
    values = [value for _label, kind in found for value in kind]
    elements = []
    for value in values:
        if value and value[0] not in elements:
            elements.append(value[0])
    if not elements:
        return ''
    if len(elements) == 1 and all(values):
        return elements[0] + ' ' + span([value[1] for value in values])
    groups = []
    for element in elements:
        labels = []
        percents = []
        for label, kind in found:
            share = [value[1] for value in kind if value and value[0] == element]
            if share:
                labels.append(label if len(share) == len(kind) else 'some ' + label)
                percents.extend(share)
        groups.append('%s %s (%s)' % (element, span(percents), ', '.join(labels)))
    return ', '.join(groups)


def insert_after(entry, after, key, value):
    out = {}
    for k, v in entry.items():
        if k != key:
            out[k] = v
        if k == after:
            out[key] = value
    return out


def main():
    mapping = json.load(open(os.path.join(HERE, 'fight_monsters.json'), encoding='utf-8'))['tasks']
    manifest = json.load(open(MANIFEST, encoding='utf-8'))
    skipped = []
    counts = {'shared': 0, 'varies': 0, 'none': 0}
    seen = set()
    for name in DOSSIERS:
        path = os.path.join(RESOURCES, name)
        before = open(path, encoding='utf-8', newline='').read()
        entries = json.loads(before)
        for i, entry in enumerate(entries):
            task = entry['task']
            if task not in mapping:
                raise SystemExit('fight_monsters.json has no entry for ' + task)
            if 'weakness' not in entry:
                raise SystemExit(task + ' has no weakness to put the field after')
            seen.add(task)
            value = elemental_weakness(mapping[task]['monsters'], manifest, skipped)
            counts['none' if not value else 'varies' if '(' in value else 'shared'] += 1
            entries[i] = insert_after(entry, 'weakness', 'elementalWeakness', value)
        after = json.dumps(entries, indent=2, ensure_ascii=True) + '\n'
        if after != before:
            with open(path, 'w', encoding='utf-8', newline='\n') as f:
                f.write(after)
            print('wrote', name)
        else:
            print('unchanged', name)
    unused = sorted(set(mapping) - seen)
    if unused:
        raise SystemExit('fight_monsters.json names tasks the dossier does not have: ' + ', '.join(unused))
    for version in sorted(set(skipped)):
        print('left out, its weakness is blank on the wiki:', version)
    print('%(shared)d entries whose monsters share one element, %(varies)d where it differs by monster, %(none)d with'
          ' none' % counts)


if __name__ == '__main__':
    sys.exit(main())
