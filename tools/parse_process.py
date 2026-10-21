#!/usr/bin/env python3
"""
Parser for the original Chasm: The Rift PROCESS.XX scripts (text, CRLF, ';' comments).

Structure:
  #stopani N                  (global)
  #mess N ... #end            message definitions (TextA/TextB/... lines, Delay)
  #proc N ... #action ... #end
      header keys: StartDelay, BackWait, EndDelay, Speed, SfxId, SfxPosxy,
                   Lock, LockMessage, CheckBack, LightRemap, GreenKey,
                   LinkSwitchAt x y, OnMessage N
      actions:     move x y dz dx | rotate x y a b c | up x y v | light x y ...
                   unlock N | playani N | ...

Usage:  python3 tools/parse_process.py <PROCESS.XX bytes file> [...]
        (or import parse_process(bytes) from build tools)
"""
import re
import sys


def _clean(line):
    # strip ';' comments and whitespace
    if ';' in line:
        line = line[:line.index(';')]
    return line.strip()


def parse_process(data):
    text = data.decode('latin1')
    procs = {}
    messages = {}
    stop_anims = []
    lines = text.replace('\r', '').split('\n')
    i = 0
    cur = None          # current block dict
    kind = None         # 'proc' | 'mess'
    in_action = False
    while i < len(lines):
        raw = lines[i]
        i += 1
        line = _clean(raw)
        if not line:
            continue
        low = line.lower()
        if low.startswith('#stopani'):
            parts = line.split()
            if len(parts) > 1:
                stop_anims.append(int(parts[1]))
            continue
        m = re.match(r'#(proc|mess)\s+(\d+)', low)
        if m:
            kind = m.group(1)
            num = int(m.group(2))
            cur = {'id': num, 'header': {}, 'actions': [], 'texts': []}
            in_action = False
            if kind == 'proc':
                procs[num] = cur
            else:
                messages[num] = cur
            continue
        if low == '#action':
            in_action = True
            continue
        if low == '#end':
            cur = None
            kind = None
            in_action = False
            continue
        if cur is None:
            continue
        if kind == 'mess':
            if low.startswith('delay'):
                cur['header']['delay'] = float(line.split()[1])
            elif low.startswith('texta') or low.startswith('textb') or \
                    low.startswith('textc') or low.startswith('textd'):
                q = re.search(r'"(.*)"', line)
                cur['texts'].append(q.group(1) if q else '')
            continue
        # proc block
        if in_action:
            parts = line.split()
            cur['actions'].append((parts[0].lower(), [_num(p) for p in parts[1:]]))
            continue
        parts = line.split()
        key = parts[0].lower()
        args = [_num(p) for p in parts[1:]]
        if key in ('lock', 'checkback', 'lightremap', 'greenkey'):
            cur['header'][key] = True
        elif key in ('startdelay', 'backwait', 'enddelay', 'speed', 'sfxid',
                     'lockmessage', 'onmessage'):
            cur['header'][key] = args[0] if args else True
        elif key == 'sfxposxy':
            cur['header'][key] = tuple(args[:2])
        elif key == 'linkswitchat':
            cur['header'][key] = tuple(args[:2])
        else:
            cur['header'][key] = args
    return {'procs': procs, 'messages': messages, 'stop_anims': stop_anims}


def _num(tok):
    try:
        return int(tok)
    except ValueError:
        try:
            return float(tok)
        except ValueError:
            return tok


def summarize(parsed):
    rows = []
    for num in sorted(parsed['procs']):
        p = parsed['procs'][num]
        acts = {}
        for name, _ in p['actions']:
            acts[name] = acts.get(name, 0) + 1
        rows.append((num, dict(p['header']), acts))
    return rows


if __name__ == '__main__':
    for path in sys.argv[1:]:
        with open(path, 'rb') as f:
            parsed = parse_process(f.read())
        print(f"== {path}: {len(parsed['procs'])} procs, {len(parsed['messages'])} messages")
        for num, hdr, acts in summarize(parsed):
            keys = ' '.join(sorted(hdr.keys()))
            print(f"  #proc {num:02d}: actions={acts} header=[{keys}]")
