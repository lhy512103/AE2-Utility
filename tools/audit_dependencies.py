"""Report Forge 1.20.1 releases without automatically changing dependency pins.

Run with Python 3.11+: python tools/audit_dependencies.py
Network metadata is evidence, not proof of Java/API/runtime compatibility.
"""
import concurrent.futures
import datetime
import json
from pathlib import Path
import tomllib
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
EXTRA_PROJECTS = {
    "ae2": "ae2", "guideme": "guideme", "jei": "jei",
    "cloth": "cloth-config", "architectury": "architectury-api", "curios": "curios",
    "neoecoae": "neoecoae", "ldlib": "ldlib", "thunderbolt": "thunderbolt-core",
    "ae2lt": "ae2-lightning-tech", "imblocker": "imblocker-original",
    "codechicken-lib": "codechicken-lib", "brandons-core": "brandons-core",
    "draconic-evolution": "draconic-evolution", "modonomicon": "modonomicon",
    "occultism": "occultism", "sophisticated-core": "sophisticated-core",
    "sophisticated-storage": "sophisticated-storage",
    "sophisticated-backpacks": "sophisticated-backpacks", "productivebees": "productivebees",
    "jade": "jade", "mega-cells": "mega", "merequester": "merequester",
    "applied-create": "applied-create", "smartbrainlib": "smartbrainlib",
    "kotlin-for-forge": "kotlin-for-forge",
}


def query_release(item):
    key, slug, current = item
    query = urllib.parse.urlencode({"loaders": '["forge"]', "game_versions": '["1.20.1"]'})
    url = f"https://api.modrinth.com/v2/project/{slug}/version?{query}"
    result = {"key": key, "project": slug, "current": current, "source": url}
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "AE2Utility-dependency-audit/1.0"})
        with urllib.request.urlopen(request, timeout=60) as response:
            releases = json.load(response)
        releases.sort(key=lambda release: release["date_published"], reverse=True)
        result["releases"] = [
            {field: release[field] for field in (
                "id", "version_number", "version_type", "date_published", "game_versions",
                "loaders", "dependencies", "files")}
            for release in releases[:5]
        ]
    except (OSError, ValueError) as error:
        result["error"] = str(error)
    return result


def main():
    with (ROOT / "gradle/libs.versions.toml").open("rb") as handle:
        catalog = tomllib.load(handle)
    projects = dict(EXTRA_PROJECTS)
    for library in catalog["libraries"].values():
        if library["module"].startswith("maven.modrinth:"):
            projects[library["version"]["ref"]] = library["module"].split(":", 1)[1]
    items = [(key, slug, catalog["versions"][key]) for key, slug in sorted(projects.items())]
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        results = list(executor.map(query_release, items))
    report = {"checked_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "results": results}
    destination = ROOT / "build/reports/dependency-audit.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for result in results:
        latest = next(iter(result.get("releases", [])), {})
        print(f"{result['key']}: {result['current']} -> {latest.get('version_number', result.get('error', 'NO RELEASE'))} [{latest.get('id', '-')}] ({latest.get('version_type', '-')})")
    print(f"Report: {destination}")
    return int(any("error" in result for result in results))


if __name__ == "__main__":
    raise SystemExit(main())
