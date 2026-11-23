"""Summarize synchronous atrace slices for one app without printing trace payloads."""
import collections
import re
import sys

pattern = re.compile(r"^\s*(.+?)-(\d+)\s+\(\s*(\d+)\).*? (\d+\.\d+): tracing_mark_write: ([BE])(?:\|(.*))?$")
stacks = collections.defaultdict(list)
totals = collections.defaultdict(lambda: [0, 0.0, 0.0, 0.0])
pid = int(sys.argv[2])
with open(sys.argv[1], encoding="utf-8", errors="replace") as trace:
    for line in trace:
        match = pattern.match(line.rstrip())
        if not match or int(match[3]) != pid:
            continue
        thread, tid, timestamp, kind = match[1], int(match[2]), float(match[4]), match[5]
        stack = stacks[tid]
        if kind == "B":
            payload = match[6] or ""
            name = payload.split("|", 1)[-1]
            stack.append([name, timestamp, 0.0])
        elif stack:
            name, start, children = stack.pop()
            elapsed = timestamp - start
            if stack:
                stack[-1][2] += elapsed
            value = totals[("main" if tid == pid else thread, name)]
            value[0] += 1
            value[1] += elapsed * 1000
            value[2] += max(0.0, elapsed - children) * 1000
            value[3] = max(value[3], elapsed * 1000)
print("thread | slice | count | total ms | self ms | max ms")
for (thread, name), value in sorted(totals.items(), key=lambda entry: entry[1][2], reverse=True)[:35]:
    print(f"{thread} | {name} | {value[0]} | {value[1]:.1f} | {value[2]:.1f} | {value[3]:.1f}")
