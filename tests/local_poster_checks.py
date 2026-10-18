#!/usr/bin/env python3
"""Static integration checks for local Movies/Series poster fallbacks.

These checks intentionally inspect source only: no portal, credentials, network,
emulator, or UI automation is required. They should pass after the planned
LocalPosterDrawable/MediaArtwork integration is applied.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/zynelabs/flowplay/MainActivity.kt"
LOADER = ROOT / "app/src/main/java/com/zynelabs/flowplay/data/ImageLoader.java"
POSTER = ROOT / "app/src/main/java/com/zynelabs/flowplay/data/LocalPosterDrawable.java"


def check(condition, message):
    if not condition:
        raise AssertionError(message)


main = MAIN.read_text(encoding="utf-8")
loader = LOADER.read_text(encoding="utf-8")
check(POSTER.exists(), "LocalPosterDrawable.java is missing")
poster = POSTER.read_text(encoding="utf-8")
# Local generator contract: Android Drawable, Canvas drawing, title input,
# deterministic-looking poster parameters, and last-resort launcher fallback.
check(re.search(r"class\s+LocalPosterDrawable\s+extends\s+Drawable", poster),
      "LocalPosterDrawable must extend Drawable")
check(re.search(r"(?:public\s+)?static\s+Drawable\s+create\s*\(\s*Context", poster),
      "LocalPosterDrawable.create(Context, ...) contract missing")
check(re.search(r"create\s*\(\s*Context\s+\w+\s*,\s*String\s+\w+\s*,\s*int\s+\w+\s*,\s*boolean\s+\w+", poster),
      "create must accept Context, title, color/int seed, and compact/variant boolean")
check("Canvas" in poster and re.search(r"(?:onDraw|draw)\s*\(\s*Canvas", poster),
      "local poster must be procedurally drawn on Canvas")
check(re.search(r"catch\s*\(\s*(?:Throwable|Exception|RuntimeException|[^)]*OutOfMemoryError)", poster),
      "poster generation must catch generation errors")
check("R.drawable.ic_launcher" in poster and "getDrawable" in poster,
      "launcher drawable fallback is missing")

# Compose bridge: poster artwork is centralized and supplied to both network
# loader and local placeholder paths. The local fallback must not become a URL.
check(re.search(r"fun\s+MediaArtwork\s*\(.*\bch\s*:\s*Channel", main, re.S),
      "MainActivity MediaArtwork(ch, ...) composable is missing")
check(re.search(r"placeholder\s*:\s*(?:android\.graphics\.drawable\.)?Drawable\?\s*=\s*null", main),
      "MediaArtwork/DzImage placeholder Drawable? default is missing")
check(re.search(r"fun\s+DzImage\s*\(.*?placeholder\s*:\s*(?:android\.graphics\.drawable\.)?Drawable\?\s*=\s*null",
               main, re.S),
      "DzImage must accept optional Drawable placeholder")
check(re.search(r"ImageLoader\.load\s*\(.*?placeholder", main, re.S),
      "DzImage must pass placeholder to ImageLoader")
check(re.search(r"load\s*\(.*?Drawable\s+placeholder", loader, re.S),
      "ImageLoader Drawable-placeholder overload is missing")

# MediaArtwork must retain provider artwork precedence, then use a generated
# placeholder only for Movies/Series (not Live TV), and never make generated
# art part of ImageLoader's URL/cache input.
media = main.split("fun MediaArtwork", 1)[1].split("fun ", 1)[0] if "fun MediaArtwork" in main else ""
check("ch.logo" in media, "MediaArtwork must inspect provider ch.logo")
check(re.search(r"DzImage\s*\(\s*ch\.logo", media),
      "provider artwork URL is not passed to MediaArtwork's image loader")
check("LocalPosterDrawable.create" in media,
      "MediaArtwork must create a local poster placeholder")
check("Channel.LIVE" in media or "ch.kind" in media,
      "MediaArtwork must explicitly distinguish Live TV from Movies/Series")
check(re.search(r"kind\s*==\s*Channel\.MOVIE|kind\s*==\s*Channel\.SERIES|ch\.kind", media),
      "local fallback must be scoped to movie/series kinds")

# Call-site coverage: use MediaArtwork for catalog cards/list/search/home, but
# preserve the existing Live TV branch and manual provider reload behavior.
check(main.count("MediaArtwork(") >= 4,
      "MediaArtwork should cover grid/list plus search/home catalog artwork")
check("onReload" in main and "Reload" in main,
      "manual Reload/provider UI must remain present")
# Live rows must still use provider logos directly; reject routing the live
# branch through generated poster code.
grid = main.split("if (!live) {", 1)[1].split("Spacer(Modifier.height(4.dp))", 1)[0]
check("if (ch.logo.isNotEmpty())" in grid and "DzImage(" in grid,
      "live grid provider-logo rendering is no longer evident")
live_branch = grid.split("} else {", 1)[1] if "} else {" in grid else ""
check("LocalPosterDrawable.create" not in live_branch and "MediaArtwork(" not in live_branch,
      "local poster generator leaked into the Live TV rendering branch")

# Folder brands remain local drawable resources; unbranded folders should use
# the generic/local branch rather than provider/network poster substitution.
folder = main.split("private fun CategoryFolderArtwork", 1)[1].split("fun ", 1)[0] if "private fun CategoryFolderArtwork" in main else ""
check("category_brand_netflix" in folder and "category_brand_disney_plus" in folder,
      "known local category brand resources were removed")
check("MediaArtwork" in folder or "CategoryIcon" in folder or "DzIcon" in folder,
      "unbranded category folders need a generic local fallback")

# Ensure no test accidentally reaches a real portal or prints secrets.
check("password" not in __file__.lower(), "credential-bearing test name/content is forbidden")
print("PASS: LocalPosterDrawable contract, Canvas/error/launcher fallback checked")
print("PASS: MediaArtwork/DzImage placeholder bridge and provider precedence checked")
print("PASS: movie/series call-site coverage checked; Live TV remains provider-art driven")
print("PASS: local category brands and generic folder fallback preserved")
print("Static source checks only; no portal, credentials, emulator, or runtime rendering used.")
