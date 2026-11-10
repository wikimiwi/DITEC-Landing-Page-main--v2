# Gera classes.dot a partir de classes.mmd (UML de classes de domínio, renderizado com Graphviz)
import re, html
src = open("classes.mmd", encoding="utf-8").read().splitlines()
classes = {}; rels = []; cur = None
for ln in src:
    t = ln.strip()
    m = re.match(r"class (\w+) \{", t)
    if m: cur = m.group(1); classes[cur] = {"st": None, "attrs": [], "ops": []}; continue
    if t == "}": cur = None; continue
    if cur:
        if t.startswith("<<"): classes[cur]["st"] = t.strip("<>"); continue
        if t.startswith("+") and "(" in t: classes[cur]["ops"].append(t); continue
        if cur and classes[cur]["st"] == "enumeration" and not t.startswith(("+", "-")): classes[cur]["attrs"].append(t); continue
        classes[cur]["attrs"].append(t); continue
    m = re.match(r'(\w+)\s*(?:"([^"]+)")?\s*(<--|-->|\*--|\.\.>)\s*(?:"([^"]+)")?\s*(\w+)\s*(?::\s*(.*))?', t)
    if m: rels.append(m.groups())
def esc(x): return html.escape(x)
def fmt(a):
    m = re.match(r"([+-])(\S+) (\S+)$", a)
    if m: return f"{m.group(1)} {m.group(3)}: {m.group(2)}"
    return a
out = ['digraph G {', ' rankdir=TB; nodesep=0.55; ranksep=0.85; splines=true; fontname="Liberation Sans";',
       ' node [shape=plain, fontname="Liberation Sans", fontsize=13]; edge [fontname="Liberation Sans", fontsize=12, color="#444444", arrowsize=0.8];']
for n, c in classes.items():
    enum = c["st"] == "enumeration"
    bg = "#f3ece0" if enum else "#e6eef6"
    h = f'<TABLE BORDER="1" COLOR="#555555" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3"><TR><TD BGCOLOR="{bg}">'
    if enum: h += '<FONT POINT-SIZE="11">«enumeration»</FONT><BR/>'
    h += f'<B>{esc(n)}</B></TD></TR><HR/>'
    for a in c["attrs"]: h += f'<TR><TD ALIGN="LEFT" BALIGN="LEFT">{esc(a if enum else fmt(a))}</TD></TR>'
    if not c["attrs"]: h += '<TR><TD> </TD></TR>'
    if c["ops"]:
        h += '<HR/>'
        for o in c["ops"]:
            m = re.match(r"\+(\w+\(.*?\))\s*(\w+)?", o); h += f'<TR><TD ALIGN="LEFT">+ {esc(o[1:])}</TD></TR>'
    h += '</TABLE>'
    out.append(f' {n} [label=<{h}>];')
for a, ma, kind, mb, b, lab in rels:
    # forma "A ma <-- mb B": a seta aponta para A; "A ma --> mb B": aponta para B; "*--": composição em A; "..>": dependência
    lab = (lab or "").strip()
    if kind == "<--": s, t, ms, mt = b, a, mb, ma
    elif kind == "-->": s, t, ms, mt = a, b, ma, mb
    elif kind == "*--": s, t, ms, mt = a, b, ma, mb
    else: s, t, ms, mt = a, b, ma, mb
    at = f'label=" {esc(lab)} ", ' if lab else ""
    if ms: at += f'taillabel="{ms}", '
    if mt: at += f'headlabel="{mt}", '
    if kind == "*--": at += 'dir=both, arrowtail=diamond, arrowhead=vee, '
    elif kind == "..>": at += 'style=dashed, arrowhead=vee, '
    else: at += 'arrowhead=vee, '
    out.append(f' {s} -> {t} [{at}labeldistance=1.6];')
out.append('}')
open("classes.dot", "w", encoding="utf-8").write("\n".join(out))
