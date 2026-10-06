import re

def strip_comments(text):
    return re.sub(r'<!--.*?-->', '', text, flags=re.S)

def find_templates(text, name):
    """Yield (start, end, body) for each {{name ...}} template, handling nesting."""
    out = []
    i = 0
    pat = re.compile(r'\{\{\s*' + re.escape(name) + r'\s*(\||\}\}|\n)', re.I)
    while True:
        m = pat.search(text, i)
        if not m:
            break
        start = m.start()
        depth = 0
        j = start
        while j < len(text):
            if text.startswith('{{', j):
                depth += 1
                j += 2
                continue
            if text.startswith('}}', j):
                depth -= 1
                j += 2
                if depth == 0:
                    break
                continue
            j += 1
        out.append((start, j, text[start:j]))
        i = j
    return out

def split_params(body):
    """Split a template body {{name|a|b=c}} into top-level params (strings)."""
    inner = body[2:-2]
    parts = []
    depth = 0
    sq = 0
    cur = ''
    k = 0
    while k < len(inner):
        if inner.startswith('{{', k):
            depth += 1; cur += '{{'; k += 2; continue
        if inner.startswith('}}', k):
            depth -= 1; cur += '}}'; k += 2; continue
        if inner.startswith('[[', k):
            sq += 1; cur += '[['; k += 2; continue
        if inner.startswith(']]', k):
            sq -= 1; cur += ']]'; k += 2; continue
        c = inner[k]
        if c == '|' and depth == 0 and sq == 0:
            parts.append(cur); cur = ''
        else:
            cur += c
        k += 1
    parts.append(cur)
    return parts[0].strip(), [p.strip() for p in parts[1:]]

COORD_A = re.compile(r'^x\s*:\s*(\d+)\s*,\s*y\s*:\s*(\d+)(?:\s*,\s*(?:plane|p|z)\s*:\s*(\d+))?\s*$', re.I)
COORD_B = re.compile(r'^(\d+)\s*,\s*(\d+)(?:\s*,\s*(\d+))?\s*$')
# A pin with display fields after the tile, such as "x:3234,y:3621,levels:28,icon:bluePin".
COORD_PIN = re.compile(r'^x\s*:\s*(\d+)\s*,\s*y\s*:\s*(\d+)((?:\s*,\s*[a-z]+\s*:[^,]*)+)$', re.I)

def coord(p):
    """(x, y, plane or None) for one coordinate parameter, or None."""
    m = COORD_A.match(p) or COORD_B.match(p)
    if m:
        return int(m.group(1)), int(m.group(2)), int(m.group(3)) if m.group(3) else None
    m = COORD_PIN.match(p)
    if m:
        fields = dict(f.split(':', 1) for f in m.group(3).split(',') if ':' in f)
        fields = {k.strip().lower(): v.strip() for k, v in fields.items()}
        plane = next((fields[k] for k in ('plane', 'p', 'z') if k in fields), None)
        if plane is not None and not plane.isdigit():
            return None
        return int(m.group(1)), int(m.group(2)), int(plane) if plane is not None else None
    return None

def map_params(body):
    """Parse a Map/LocLine template into dict with coords list [(x,y,plane_or_None)] and named params."""
    name, params = split_params(body)
    named = {}
    coords = []
    for p in params:
        c = coord(p)
        if c:
            coords.append(c)
            continue
        if '=' in p:
            k, v = p.split('=', 1)
            named[k.strip().lower()] = v.strip()
    if not coords and 'x' in named and 'y' in named:
        try:
            coords.append((int(named['x']), int(named['y']), None))
        except ValueError:
            pass
    plane = named.get('plane')
    try:
        plane = int(plane) if plane not in (None, '') else 0
    except ValueError:
        plane = None
    coords = [(x, y, z if z is not None else plane) for (x, y, z) in coords]
    return {'template': name, 'named': named, 'coords': coords}

def links(text):
    return [m.group(1).strip() for m in re.finditer(r'\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|[^\]]*)?\]\]', text)]

def parse_tables(text):
    """Yield tables as (header_cells, rows) where rows are lists of cell strings."""
    text = strip_comments(text)
    tables = []
    i = 0
    while True:
        s = text.find('\n{|', i)
        if s < 0:
            break
        # find matching |} at line start with nesting of tables
        depth = 0
        j = s + 1
        lines = []
        pos = j
        end = None
        while pos < len(text):
            nl = text.find('\n', pos)
            if nl < 0: nl = len(text)
            line = text[pos:nl]
            if line.startswith('{|'):
                depth += 1
            elif line.startswith('|}'):
                depth -= 1
                if depth == 0:
                    end = nl
                    break
            pos = nl + 1
        if end is None:
            break
        tables.append(text[s+1:end])
        i = end
    out = []
    for t in tables:
        out.append(parse_table(t))
    return out

def parse_table(t):
    # split into rows at top-level '|-' lines (template depth 0)
    rows = []
    cur = []
    depth = 0
    lines = t.split('\n')[1:]  # drop {| line
    buf = ''
    rows_text = []
    for line in lines:
        if depth == 0 and line.startswith('|-'):
            rows_text.append(buf); buf = ''; continue
        if depth == 0 and line.startswith('|}'):
            break
        # A caption line would otherwise turn the header row into a data row.
        if depth == 0 and line.startswith('|+'):
            continue
        buf += line + '\n'
        depth += line.count('{{') - line.count('}}')
        if depth < 0: depth = 0
    rows_text.append(buf)
    header = []
    data = []
    for rt in rows_text:
        cells = split_cells(rt)
        if not cells:
            continue
        if all(c[0] == '!' for c in cells):
            header = [c[1] for c in cells]
        else:
            data.append([c[1] for c in cells])
    return {'header': header, 'rows': data}

def split_cells(rt):
    """Split a row's text into cells [(kind, text)] where kind is '!' or '|'."""
    cells = []
    depth = 0
    sq = 0
    k = 0
    cur = None
    kind = None
    at_line_start = True
    while k < len(rt):
        if rt.startswith('{{', k):
            depth += 1
            if cur is not None: cur += '{{'
            k += 2; at_line_start = False; continue
        if rt.startswith('}}', k):
            depth = max(0, depth - 1)
            if cur is not None: cur += '}}'
            k += 2; at_line_start = False; continue
        if rt.startswith('[[', k):
            sq += 1
            if cur is not None: cur += '[['
            k += 2; at_line_start = False; continue
        if rt.startswith(']]', k):
            sq = max(0, sq - 1)
            if cur is not None: cur += ']]'
            k += 2; at_line_start = False; continue
        c = rt[k]
        if at_line_start and depth == 0 and c in '|!':
            if cur is not None:
                cells.append((kind, cur))
            kind = c
            cur = ''
            k += 1
            at_line_start = False
            continue
        if depth == 0 and sq == 0 and cur is not None and (rt.startswith('||', k) or (kind == '!' and rt.startswith('!!', k))):
            cells.append((kind, cur))
            cur = ''
            k += 2
            continue
        if cur is not None:
            cur += c
        at_line_start = (c == '\n')
        k += 1
    if cur is not None:
        cells.append((kind, cur))
    # strip cell attributes like 'rowspan="2" | text' (only at depth 0, before first '|')
    out = []
    for kind, txt in cells:
        m = re.match(r'^\s*((?:rowspan|colspan|style|class|width|align|data-sort-value)\s*=\s*"[^"]*"\s*)+\|(.*)$', txt, re.S)
        attrs = ''
        if m:
            attrs = m.group(1)
            txt = m.group(2)
        out.append((kind, txt.strip(), attrs))
    return [(k, t) for k, t, a in out]

def _plain_template(match):
    """Readable text for the templates that carry meaning in a requirement sentence. Others are dropped."""
    name, params = split_params('{{' + match.group(1) + '}}')
    name = name.strip().lower()
    plain = [p for p in params if '=' not in p]
    if name == 'scp' and plain:
        if plain[0].lower() == 'quest':
            return ''
        return plain[1] + ' ' + plain[0] if len(plain) > 1 else plain[0]
    if name == 'boostable' and plain:
        return ' (not boostable)' if plain[0].lower().startswith('n') else ' (boostable)'
    if name == 'fairycode' and plain:
        return plain[0].upper()
    return ''


def plain_text(text):
    """Wikitext as the sentences a reader sees: links as their text, no formatting, references or other
    templates, and whitespace collapsed. {{SCP|Agility|60}} reads as "60 Agility" and {{Boostable|no}} as
    "(not boostable)", so a quoted requirement keeps its skill and level."""
    text = strip_comments(text)
    text = re.sub(r'<ref[^>]*/>', '', text)
    text = re.sub(r'<ref[^>]*>.*?</ref>', '', text, flags=re.S)
    text = re.sub(r'<br\s*/?>', ' ', text, flags=re.I)
    text = re.sub(r'<[^>]+>', '', text)
    for _ in range(5):
        text = re.sub(r'\{\{([^{}]*)\}\}', _plain_template, text)
    for _ in range(3):
        text = re.sub(r'\[\[(?:File|Image):[^\[\]]*\]\]', '', text, flags=re.I)
    text = re.sub(r'\[\[([^\]|]+)\|([^\]]+)\]\]', r'\2', text)
    text = re.sub(r'\[\[([^\]]+)\]\]', r'\1', text)
    text = re.sub(r"'{2,}", '', text)
    text = text.replace('’', "'").replace(' ', ' ')
    return re.sub(r'\s+', ' ', text).strip()


def infobox_field(text, field):
    """A field of the page's first infobox as plain text, or None."""
    text = strip_comments(text)
    m = re.search(r'\{\{\s*(Infobox[^|\n}]*)', text)
    boxes = find_templates(text, m.group(1).strip()) if m else []
    if not boxes:
        return None
    _name, params = split_params(boxes[0][2])
    for p in params:
        if '=' in p:
            key, value = p.split('=', 1)
            if key.strip().lower() == field.lower():
                return plain_text(value)
    return None


def yesno(cell):
    c = cell.strip()
    l = c.lower()
    if re.search(r'\{\{\s*yes', l): return 'yes'
    if re.search(r'\{\{\s*no\b', l) or re.search(r'\{\{\s*no\s*\}\}', l): return 'no'
    if 'partial' in l or 'some' in l or 'mixed' in l: return 'partial'
    return 'unclear:' + c[:60]
