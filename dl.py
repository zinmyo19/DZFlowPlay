#!/usr/bin/env python3
"""POM-walking Maven dependency downloader for the Compose experiment.

Resolves compile+runtime transitive deps of the requested artifacts from
Google Maven and Maven Central, downloading AARs/JARs into this project's
deps/ directory.
Skips: optional deps, test/provided scopes, lint/test artifacts.
"""
import os, re, sys, time, hashlib
import xml.etree.ElementTree as ET
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

PROJECT = os.path.dirname(os.path.abspath(__file__))
DEPS = os.path.join(PROJECT, "deps")
CACHE = os.path.join(DEPS, ".pomcache")
os.makedirs(DEPS, exist_ok=True)
os.makedirs(CACHE, exist_ok=True)

GOOGLE_REPO = "https://dl.google.com/dl/android/maven2"
CENTRAL_REPO = "https://repo.maven.apache.org/maven2"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

# Top-level artifacts: (group, artifact, version)
TOP = [
    ("androidx.compose.ui", "ui", "1.6.8"),
    ("androidx.compose.foundation", "foundation", "1.6.8"),
    ("androidx.compose.material3", "material3", "1.2.1"),
    ("androidx.activity", "activity-compose", "1.9.2"),
]

SKIP_ARTIFACTS = ("lint", "test", "testing", "benchmark")
SKIP_SUFFIX = ("-lint", "-testing", "-test", "-benchmark", "-screenshot-test")

UA = {"User-Agent": "compose-test-dl/1.0"}
_last_req = [0.0]

def fetch(url):
    # polite rate limiting
    dt = time.time() - _last_req[0]
    if dt < 0.25:
        time.sleep(0.25 - dt)
    _last_req[0] = time.time()
    last = None
    for attempt in range(3):
        try:
            req = Request(url, headers=UA)
            with urlopen(req, timeout=60) as r:
                return r.read()
        except HTTPError as e:
            if e.code == 404:
                raise RuntimeError("HTTP 404 %s" % url)
            last = e
            time.sleep(1.5 * (attempt + 1))
        except (URLError, TimeoutError) as e:
            last = e
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError("fetch failed %s: %s" % (url, last))

def pom_url(group, artifact, version):
    path = "%s/%s/%s/%s-%s.pom" % (
        group.replace(".", "/"), artifact, version, artifact, version)
    return path

def repos_for(group):
    """Prefer Google Maven for AndroidX/Google artifacts, Central otherwise."""
    if group.startswith(("androidx.", "com.android.", "com.google.android.")):
        return (GOOGLE_REPO, CENTRAL_REPO)
    return (CENTRAL_REPO, GOOGLE_REPO)

def get_pom(group, artifact, version):
    key = hashlib.md5(("%s:%s:%s" % (group, artifact, version)).encode()).hexdigest()
    cp = os.path.join(CACHE, key + ".pom")
    if os.path.exists(cp):
        with open(cp, "rb") as f:
            return f.read()
    last = None
    for repo in repos_for(group):
        try:
            data = fetch(repo + "/" + pom_url(group, artifact, version))
            with open(cp, "wb") as f:
                f.write(data)
            return data
        except RuntimeError as e:
            last = e
    raise RuntimeError("POM not found %s:%s:%s (%s)" % (group, artifact, version, last))

def txt(el, tag):
    c = el.find("m:%s" % tag, NS)
    return c.text.strip() if c is not None and c.text else None

def collect_props_and_mgmt(root):
    """Return (properties dict, managed dict of (g,a)->v) including parents."""
    props, mgmt = {}, {}
    chain = []
    cur = root
    seen = set()
    while cur is not None:
        chain.append(cur)
        p = cur.find("m:parent", NS)
        if p is None:
            break
        g, a, v = txt(p, "groupId"), txt(p, "artifactId"), txt(p, "version")
        if not (g and a and v) or (g, a, v) in seen:
            break
        seen.add((g, a, v))
        try:
            data = get_pom(g, a, v)
        except RuntimeError:
            break
        cur = ET.fromstring(data)
    # apply from topmost parent down so children override
    for node in reversed(chain):
        pr = node.find("m:properties", NS)
        if pr is not None:
            for e in pr:
                tag = e.tag.split("}", 1)[-1]
                if e.text:
                    props[tag] = e.text.strip()
        dm = node.find("m:dependencyManagement/m:dependencies", NS)
        if dm is not None:
            for d in dm.findall("m:dependency", NS):
                g, a, v = txt(d, "groupId"), txt(d, "artifactId"), txt(d, "version")
                scope = txt(d, "scope") or "compile"
                typ = txt(d, "type") or "jar"
                if g and a and v and scope in ("compile", "runtime") and typ != "test-jar":
                    mgmt[(g, a)] = v
    return props, mgmt

_var = re.compile(r"\$\{([^}]+)\}")
def norm_version(v):
    """Normalize Maven version ranges: [1.6.8] -> 1.6.8, [1.0,2.0) -> 1.0."""
    if v and v[0] in "[(":
        v = v.strip("[]()")
        v = v.split(",")[0].strip()
    return v
def subst(s, props):
    if not s:
        return s
    def rep(m):
        return props.get(m.group(1), m.group(0))
    for _ in range(5):
        ns = _var.sub(rep, s)
        if ns == s:
            break
        s = ns
    return s

def parse_deps(root, props, mgmt):
    deps = []
    ds = root.find("m:dependencies", NS)
    if ds is None:
        return deps
    for d in ds.findall("m:dependency", NS):
        g = subst(txt(d, "groupId"), props)
        a = subst(txt(d, "artifactId"), props)
        v = subst(txt(d, "version"), props)
        scope = txt(d, "scope") or "compile"
        typ = txt(d, "type") or "jar"
        optional = (txt(d, "optional") == "true")
        if not g or not a:
            continue
        if scope not in ("compile", "runtime"):
            continue
        if optional:
            continue
        if typ not in ("jar", "aar", "bundle"):
            continue
        if any(s in a for s in SKIP_ARTIFACTS) or a.endswith(SKIP_SUFFIX):
            continue
        if v is None or "${" in (v or ""):
            v = mgmt.get((g, a))
        if not v:
            print("  WARN: no version for %s:%s (skipped)" % (g, a))
            continue
        excl = set()
        ex = d.find("m:exclusions", NS)
        if ex is not None:
            for e in ex.findall("m:exclusion", NS):
                eg, ea = txt(e, "groupId"), txt(e, "artifactId")
                excl.add((eg, ea))
        deps.append((g, a, v, typ, excl))
    return deps

def download_artifact(g, a, v, typ, _fallback=True):
    ext = "aar" if typ == "aar" else "jar"
    fn = "%s-%s.%s" % (a, v, ext)
    dest = os.path.join(DEPS, fn)
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return dest, True
    path = "%s/%s/%s/%s" % (g.replace(".", "/"), a, v, fn)
    last = None
    for repo in repos_for(g):
        try:
            data = fetch(repo + "/" + path)
            with open(dest, "wb") as f:
                f.write(data)
            return dest, False
        except RuntimeError as e:
            last = e
    # type fallback once: parent POMs often omit <type>, defaulting to jar
    # for artifacts that are actually aar (and vice versa)
    if _fallback:
        other = "jar" if ext == "aar" else "aar"
        return download_artifact(g, a, v, other, _fallback=False)
    raise RuntimeError("download failed %s:%s:%s (%s)" % (g, a, v, last))

resolved = {}   # (g,a) -> (v, typ)
visiting = set()

def ver_key(v):
    """Sortable key for Maven versions: numeric parts first, then suffix."""
    parts = []
    for p in str(v).replace("-", ".").split("."):
        parts.append((0, int(p)) if p.isdigit() else (1, p))
    return parts

def resolve(g, a, v, typ, excl):
    key = (g, a)
    if key in excl or key in visiting:
        return
    v = norm_version(v)
    if key in resolved:
        # newest-wins: replace if the new version is newer (fixes mixed
        # 2.3.1/2.6.1 lifecycle franken-deps that dropped DefaultLifecycleObserver)
        old_v, old_typ = resolved[key]
        if ver_key(v) <= ver_key(old_v):
            return
        # fall through to re-resolve with the newer version
    resolved[key] = (v, typ)
    visiting.add(key)
    try:
        data = get_pom(g, a, v)
    except RuntimeError as e:
        print("  WARN: %s" % e)
        visiting.discard(key)
        return
    root = ET.fromstring(data)
    props, mgmt = collect_props_and_mgmt(root)
    props["project.version"] = v
    for (dg, da, dv, dtyp, dexcl) in parse_deps(root, props, mgmt):
        if (dg, da) in excl:
            continue
        resolve(dg, da, dv, dtyp, dexcl | excl)
    visiting.discard(key)

def main():
    print("resolving...")
    for g, a, v in TOP:
        resolve(g, a, v, "aar", set())
    print("resolved %d artifacts" % len(resolved))
    # Toolchain artifacts are not necessarily reachable from the app POM
    # graph, so fetch the exact versions required by build.sh explicitly.
    extra = [
        (("androidx.compose.compiler", "compiler", "1.5.14"), "jar"),
        (("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "1.9.24"), "jar"),
        (("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.24"), "jar"),
        (("org.jetbrains.kotlin", "kotlin-stdlib", "1.8.0"), "jar"),
        (("org.jetbrains.kotlin", "kotlin-script-runtime", "1.9.24"), "jar"),
        (("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"), "jar"),
        (("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"), "jar"),
        (("org.jetbrains", "annotations", "13.0"), "jar"),
    ]
    n_dl = 0
    for (g, a), (v, typ) in sorted(resolved.items()):
        try:
            dest, cached = download_artifact(g, a, v, typ)
            if not cached:
                n_dl += 1
            print("  %s %s:%s:%s" % ("cached" if cached else "got", g, a, v))
        except RuntimeError as e:
            print("  FAIL: %s" % e)
    for (g, a, v), typ in extra:
        try:
            dest, cached = download_artifact(g, a, v, typ)
            print("  %s %s:%s:%s (toolchain)" % ("cached" if cached else "got", g, a, v))
            if not cached:
                n_dl += 1
        except RuntimeError as e:
            print("  FAIL: %s" % e)
    # manifest of what we have
    with open(os.path.join(DEPS, "MANIFEST.txt"), "w") as f:
        for (g, a), (v, typ) in sorted(resolved.items()):
            f.write("%s:%s:%s [%s]\n" % (g, a, v, typ))
    print("downloaded %d new files into %s" % (n_dl, DEPS))

if __name__ == "__main__":
    main()
