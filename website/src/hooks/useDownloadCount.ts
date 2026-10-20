import { useEffect, useState } from 'react';

const RELEASES_API = 'https://api.github.com/repos/kdroidFilter/Zayit/releases';

// Only the files people install: the updater's feeds (.yml), silent updates (-nsis.exe), blockmaps and signatures aren't downloads
const INSTALLER = /\.(exe|msi|pkg|dmg|zip|deb|rpm|pacman)$/i;
const UPDATER = /-nsis\.exe$/i;

async function fetchDownloadCount(): Promise<number> {
  let total = 0;
  for (let page = 1; ; page++) {
    const resp = await fetch(`${RELEASES_API}?per_page=100&page=${page}`);
    if (!resp.ok) throw new Error(`GitHub ${resp.status}`);
    const releases: { assets: { name: string; download_count: number }[] }[] = await resp.json();
    for (const release of releases) {
      for (const asset of release.assets) {
        if (INSTALLER.test(asset.name) && !UPDATER.test(asset.name)) total += asset.download_count;
      }
    }
    if (releases.length < 100) return total;
  }
}

/** The installers downloaded across every release, from the GitHub API; null until known (or if GitHub fails). */
export function useDownloadCount(): number | null {
  const [count, setCount] = useState<number | null>(null);
  useEffect(() => {
    fetchDownloadCount().then(setCount).catch(() => setCount(null));
  }, []);
  return count;
}
