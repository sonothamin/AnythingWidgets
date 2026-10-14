"""Fails if a registry spec would be rejected by the app's validator (SpecValidator in SpecCodec.kt).

Mirrors the numeric and structural limits only. Keep it in step with SpecCodec.kt when those change.
Run by publish.sh, so CI catches a bad spec before it reaches the registry instead of at install time.
"""
import json, re, sys, glob, os
os.chdir(os.path.dirname(os.path.abspath(__file__)))
CONT={"row","column","box"}; TYPES=CONT|{"text","spacer","progress","glyph","dotgrid","clock"}
FONTS={"dots","serif","oldstandard","sans","mono"}
CLAUSE=re.compile(r"(!)?([A-Za-z0-9_.]{1,64})(?:\s*(==|!=|<=|>=|<|>)\s*('[^']{0,32}'|[^\s'<>=!&|]{1,32}))?")
PATTERN=re.compile(r"[#r._/\s]*")
def cond(e): return len(e)<=160 and all(CLAUSE.fullmatch(c.strip()) for o in e.split("||") for c in o.split("&&"))
def check(spec):
    errs=[]
    def walk(n,depth,cnt):
        cnt[0]+=1
        if cnt[0]>100: errs.append("Too many nodes"); return
        if depth>10: errs.append("Nesting too deep")
        t=n.get("type")
        if t not in TYPES: errs.append(f"Unknown type {t}")
        if n.get("children") and t not in CONT: errs.append(f"{t} has children")
        p,g,r=n.get("padding",0),n.get("gap",0),n.get("radius",0)
        if not(0<=p<=48 and 0<=g<=32 and 0<=r<=200): errs.append(f"Spacing out of range p={p} g={g} r={r}")
        s=n.get("size",14)
        if not 8<=s<=200: errs.append(f"Text size {s}")
        for k in ("width","height"):
            if n.get(k) is not None and not 1<=n[k]<=400: errs.append(f"{k} {n[k]}")
        if len(n.get("text") or "")>200: errs.append("Text too long")
        if n.get("visible") is not None and not cond(n["visible"]): errs.append("Bad condition: "+n["visible"])
        if t=="glyph" and n.get("font") and n["font"] not in FONTS: errs.append("font")
        if t!="glyph" and any(n.get(k) is not None for k in("dropcap","minSize","inset")): errs.append("glyph-only prop")
        if n.get("dropcap") is not None and not 2<=n["dropcap"]<=4: errs.append("dropcap")
        if n.get("minSize") is not None and not 8<=n["minSize"]<=s: errs.append("minSize")
        if n.get("inset") is not None and not 0<=n["inset"]<=48: errs.append(f"Inset out of range {n['inset']}")
        pool=n.get("pool",[])
        if len(pool)>32 or any(len(x)>160 for x in pool): errs.append("Pool too large")
        if t=="dotgrid":
            c,rw=n.get("columns",0),n.get("rows",0)
            if not(1<=c<=80 and 1<=rw<=80 and c*rw<=800): errs.append("dot grid")
            pt=n.get("pattern") or ""
            if len(pt)>1024: errs.append("pattern long")
            if "{{" not in pt and not PATTERN.fullmatch(pt): errs.append("pattern chars: "+pt[:30])
        for ch in n.get("children",[]): walk(ch,depth+1,cnt)
    for k in ("root","compact","large"):
        if spec.get(k): walk(spec[k],1,[0])
    return errs
bad=0
for f in sorted(glob.glob("widgets/*.json")):
    e=check(json.load(open(f)))
    if e: print("FAIL", f, sorted(set(e))[:6])
    bad+=bool(e)
print(f"{bad} spec(s) would be rejected by the app" if bad else "all specs pass the app's validator")
sys.exit(1 if bad else 0)
