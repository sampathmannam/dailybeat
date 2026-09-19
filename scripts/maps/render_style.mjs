import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';

const [modulePath, outputDir] = process.argv.slice(2);
const {layers, namedFlavor} = await import(pathToFileURL(path.resolve(modulePath)).href);
for (const theme of ['light', 'dark']) {
  const style = {
    version: 8,
    name: `DailyBeat Tamil Nadu ${theme}`,
    glyphs: '__PACK_ROOT__/fonts/{fontstack}/{range}.pbf',
    sprite: `__PACK_ROOT__/sprites/v4/${theme}`,
    sources: {protomaps: {
      type: 'vector',
      url: 'pmtiles://__PACK_ROOT__/tamil-nadu.pmtiles',
      attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a> · <a href="https://protomaps.com">Protomaps</a>' ,
    }},
    layers: layers('protomaps', namedFlavor(theme), {lang: 'en'}),
  };
  fs.writeFileSync(path.join(outputDir, `${theme}.json`), JSON.stringify(style));
}
