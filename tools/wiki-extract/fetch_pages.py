#!/usr/bin/env python3
"""Fetches the OSRS Wiki pages that build_slayer_areas.py and build_fight_fields.py read, into cache/.

Reads the page titles from curated.json (task guide pages, monster pages and extra location pages),
access.json (the pages its requirements were read from) and fight_monsters.json (the monster pages of
each dossier entry), resolves redirects with one API call per 40 titles, and downloads the raw wikitext
of every page that is not cached yet. One request per second, with a descriptive User-Agent. Run with
--refresh to fetch everything again.
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from datetime import date

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, 'cache')
MANIFEST = os.path.join(CACHE, 'manifest.json')
BASE = 'https://oldschool.runescape.wiki'
USER_AGENT = 'SlayerCompanion-data-builder/1.0 (personal RuneLite plugin)'
DELAY_SECONDS = 1.0


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as response:
        return response.read().decode('utf-8')


def cache_file(title):
    return os.path.join(CACHE, re.sub(r'[^A-Za-z0-9_.-]+', '_', title) + '.txt')


def cited_pages(value):
    """Every "page" named anywhere in an access.json value."""
    if isinstance(value, dict):
        found = [value['page']] if isinstance(value.get('page'), str) else []
        for item in value.values():
            found.extend(cited_pages(item))
        return found
    if isinstance(value, list):
        return [page for item in value for page in cited_pages(item)]
    return []


def fight_titles(fight):
    """The monster pages fight_monsters.json names, without their version selectors."""
    titles = []
    for task in fight['tasks'].values():
        for pages in task['monsters'].values():
            titles.extend(page.split('#')[0] for page in pages)
    return titles


def wanted_titles(curated, access=None, fight=None):
    titles = []
    for task in curated['tasks']:
        if task.get('page'):
            titles.append(task['page'])
        titles.extend(task.get('monsters', []))
    titles.extend(curated.get('extraPages', []))
    krystilia = curated.get('krystilia')
    if krystilia:
        titles.append(krystilia['page'])
        for monsters in krystilia.get('monsters', {}).values():
            titles.extend(monsters)
    if access:
        titles.extend(cited_pages(access.get('locations', {})))
        titles.extend(cited_pages(access.get('open', {})))
    if fight:
        titles.extend(fight_titles(fight))
    seen = set()
    return [t for t in titles if not (t in seen or seen.add(t))]


def resolve(titles):
    result = {}
    for i in range(0, len(titles), 40):
        chunk = titles[i:i + 40]
        query = urllib.parse.urlencode({'action': 'query', 'titles': '|'.join(chunk), 'redirects': 1, 'format': 'json'})
        data = json.loads(get(BASE + '/api.php?' + query))
        time.sleep(DELAY_SECONDS)
        normalized = {n['from']: n['to'] for n in data['query'].get('normalized', [])}
        redirects = {n['from']: n['to'] for n in data['query'].get('redirects', [])}
        missing = {p['title'] for p in data['query']['pages'].values() if 'missing' in p}
        for title in chunk:
            final = normalized.get(title, title)
            final = redirects.get(final, final).split('#')[0]
            result[title] = None if final in missing else final
    return result


def main():
    refresh = '--refresh' in sys.argv
    curated = json.load(open(os.path.join(HERE, 'curated.json'), encoding='utf-8'))
    access_path = os.path.join(HERE, 'access.json')
    access = json.load(open(access_path, encoding='utf-8')) if os.path.exists(access_path) else None
    fight_path = os.path.join(HERE, 'fight_monsters.json')
    fight = json.load(open(fight_path, encoding='utf-8')) if os.path.exists(fight_path) else None
    os.makedirs(CACHE, exist_ok=True)
    manifest = json.load(open(MANIFEST, encoding='utf-8')) if os.path.exists(MANIFEST) else {}
    # A page already cached under its own title, as the target of a redirect, needs no request either.
    cached = set(manifest) | {entry['final'] for entry in manifest.values()}
    titles = [t for t in wanted_titles(curated, access, fight) if refresh or t not in cached]
    if titles:
        for title, final in resolve(titles).items():
            if not final:
                print('missing on the wiki:', title)
                continue
            path = cache_file(final)
            if refresh or not os.path.exists(path):
                text = get(BASE + '/w/' + urllib.parse.quote(final.replace(' ', '_')) + '?action=raw')
                with open(path, 'w', encoding='utf-8', newline='\n') as f:
                    f.write(text)
                time.sleep(DELAY_SECONDS)
            manifest[title] = {'final': final, 'fetched': date.today().isoformat()}
    with open(MANIFEST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(manifest, f, indent=1, sort_keys=True)
    print(len(manifest), 'pages in the cache manifest')


if __name__ == '__main__':
    main()
