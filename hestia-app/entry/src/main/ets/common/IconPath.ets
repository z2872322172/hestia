/**
 * 线性图标路径表。
 *
 * 坐标统一在 24×24 视口内，由 NavIcon 用 Shape.viewPort 缩放到目标尺寸。
 * 路径取自 design/hestia-app-ui.html 里的 SVG，rect/circle 已转成等价 path 命令。
 */
export class IconPath {
  static readonly HOME: string =
    'M3 10 L12 3 L21 10 L21 19 A2 2 0 0 1 19 21 L5 21 A2 2 0 0 1 3 19 Z M9 21 L9 15 L15 15 L15 21';

  static readonly ORDER: string =
    'M6 3 L18 3 A1 1 0 0 1 19 4 L19 21 L12 17 L5 21 L5 4 A1 1 0 0 1 6 3 Z';

  static readonly MESSAGE: string =
    'M4 5 L20 5 A1 1 0 0 1 21 6 L21 16 A1 1 0 0 1 20 17 L8 17 L3 21 L3 6 A1 1 0 0 1 4 5 Z';

  static readonly PROFILE: string =
    'M12 12 A4 4 0 1 0 12 4 A4 4 0 1 0 12 12 Z M4 21 C4 17 7 15 12 15 C17 15 20 17 20 21';

  static readonly SEARCH: string =
    'M11 4 A7 7 0 1 0 11 18 A7 7 0 1 0 11 4 Z M16.5 16.5 L21 21';

  static readonly LOCATION: string =
    'M21 10 C21 17 12 23 12 23 C12 23 3 17 3 10 A9 9 0 0 1 21 10 Z ' +
    'M12 13 A3 3 0 1 0 12 7 A3 3 0 1 0 12 13 Z';

  static readonly BELL: string =
    'M18 8 A6 6 0 1 0 6 8 C6 15 3 17 3 17 L21 17 C21 17 18 15 18 8 Z ' +
    'M13.7 21 A2 2 0 0 1 10.3 21';

  static readonly BACK: string = 'M15 18 L9 12 L15 6';

  static readonly CHEVRON: string = 'M9 18 L15 12 L9 6';

  static readonly STAR: string =
    'M12 2 L15.09 8.26 L22 9.27 L17 14.14 L18.18 21.02 L12 17.77 ' +
    'L5.82 21.02 L7 14.14 L2 9.27 L8.91 8.26 Z';

  static readonly SPARKLE: string =
    'M12 3.5 L13.9 9 L19.5 10.9 L13.9 12.8 L12 18.5 L10.1 12.8 L4.5 10.9 L10.1 9 Z ' +
    'M18.5 16.5 L19.3 19 L21.5 19.8 L19.3 20.5 L18.5 23 L17.8 20.5 L15.5 19.8 L17.8 19 Z';

  static readonly GRID: string =
    'M5.3 3.5 L8.7 3.5 A1.8 1.8 0 0 1 10.5 5.3 L10.5 8.7 A1.8 1.8 0 0 1 8.7 10.5 ' +
    'L5.3 10.5 A1.8 1.8 0 0 1 3.5 8.7 L3.5 5.3 A1.8 1.8 0 0 1 5.3 3.5 Z ' +
    'M15.3 3.5 L18.7 3.5 A1.8 1.8 0 0 1 20.5 5.3 L20.5 8.7 A1.8 1.8 0 0 1 18.7 10.5 ' +
    'L15.3 10.5 A1.8 1.8 0 0 1 13.5 8.7 L13.5 5.3 A1.8 1.8 0 0 1 15.3 3.5 Z ' +
    'M5.3 13.5 L8.7 13.5 A1.8 1.8 0 0 1 10.5 15.3 L10.5 18.7 A1.8 1.8 0 0 1 8.7 20.5 ' +
    'L5.3 20.5 A1.8 1.8 0 0 1 3.5 18.7 L3.5 15.3 A1.8 1.8 0 0 1 5.3 13.5 Z ' +
    'M15.3 13.5 L18.7 13.5 A1.8 1.8 0 0 1 20.5 15.3 L20.5 18.7 A1.8 1.8 0 0 1 18.7 20.5 ' +
    'L15.3 20.5 A1.8 1.8 0 0 1 13.5 18.7 L13.5 15.3 A1.8 1.8 0 0 1 15.3 13.5 Z';

  static readonly SHIELD: string =
    'M12 22 C12 22 20 18 20 12 L20 5 L12 2 L4 5 L4 12 C4 18 12 22 12 22 Z M9 12 L11 14 L15 10';

  static readonly CLOCK: string = 'M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z M12 7 L12 12 L15 14';

  static readonly REWORK: string =
    'M20 12 A8 8 0 1 1 17.7 6.4 M20 4 L20 8 L16 8';

  static readonly GUARANTEE: string =
    'M3 7 A2 2 0 0 1 5 5 L19 5 A2 2 0 0 1 21 7 L21 17 A2 2 0 0 1 19 19 ' +
    'L5 19 A2 2 0 0 1 3 17 Z M3 10 L21 10';

  static readonly PLUS: string = 'M12 5 L12 19 M5 12 L19 12';

  static readonly CHECK: string = 'M5 13 L9 17 L19 7';

  static readonly EDIT: string = 'M4 20 L8 20 L18 10 L14 6 L4 16 L4 20 Z M14 6 L18 10';

  static readonly TRASH: string =
    'M4 7 L20 7 M9 7 L9 5 L15 5 L15 7 M6 7 L7 20 L17 20 L18 7';

  static readonly COPY: string =
    'M9 9 L19 9 L19 19 L9 19 Z M5 15 L5 5 L15 5';

  static readonly COUPON: string =
    'M3 8 L21 8 L21 11 A2 2 0 0 0 21 15 L21 18 L3 18 L3 15 A2 2 0 0 0 3 11 Z';

  static readonly SERVICE: string =
    'M4 13 A8 8 0 0 1 20 13 M4 13 L4 17 L7 17 L7 13 Z M17 13 L17 17 L20 17 L20 13 Z';

  static readonly SETTING: string =
    'M12 9 A3 3 0 1 0 12 15 A3 3 0 1 0 12 9 Z ' +
    'M12 2 L12 5 M12 19 L12 22 M2 12 L5 12 M19 12 L22 12 ' +
    'M4.9 4.9 L7 7 M17 17 L19.1 19.1 M19.1 4.9 L17 7 M7 17 L4.9 19.1';

  static readonly HOUSE_FILLED: string =
    'M3 10 L12 3 L21 10 L21 19 A2 2 0 0 1 19 21 L5 21 A2 2 0 0 1 3 19 Z';

  static readonly CAMERA: string =
    'M4 8 L8 8 L10 5 L14 5 L16 8 L20 8 L20 19 L4 19 Z M12 16 A3.5 3.5 0 1 0 12 9 A3.5 3.5 0 1 0 12 16 Z';

  static readonly EYE: string =
    'M2 12 C2 12 5 5 12 5 C19 5 22 12 22 12 C22 12 19 19 12 19 C5 19 2 12 2 12 Z ' +
    'M12 15 A3 3 0 1 0 12 9 A3 3 0 1 0 12 15 Z';

  static readonly EYE_OFF: string =
    'M2 12 C2 12 5 5 12 5 C19 5 22 12 22 12 C22 12 19 19 12 19 C5 19 2 12 2 12 Z ' +
    'M4 4 L20 20';
}
