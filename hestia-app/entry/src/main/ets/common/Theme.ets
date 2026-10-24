/**
 * 设计规范常量。
 *
 * 所有色值、字号、间距只在这里定义一次，页面里禁止出现 #F0653A 这类字面量，
 * 否则后期调色要改十几个文件。
 *
 * 设计基准：360 × 780 vp（HarmonyOS 手机）。
 */
export class Palette {
  /** 主色：按钮、价格、选中态、品牌头 */
  static readonly brand: string = '#F0653A';
  /** 主色深：按下态、深色小标签 */
  static readonly brandDeep: string = '#DC5528';
  /** 主色浅：标签底、图标底 */
  static readonly brandLight: string = '#FFE7DC';
  /** 主色极浅：选中行底色 */
  static readonly brandXLight: string = '#FFF4EF';

  /** 正文：标题、主要信息 */
  static readonly ink: string = '#1C1917';
  /** 次要文字：描述、说明 */
  static readonly ink2: string = '#6B6560';
  /** 辅助文字：时间、计数、提示 */
  static readonly ink3: string = '#A29A93';

  /** 分割线 */
  static readonly line: string = '#F0EAE3';
  /** 页面底色（米白） */
  static readonly bg: string = '#FBF8F5';
  /** 卡片 */
  static readonly surface: string = '#FFFFFF';

  static readonly ok: string = '#1F9D6B';
  static readonly warn: string = '#D98324';
  static readonly danger: string = '#D64545';

  /** 分类图标底色 */
  static readonly cat1: string = '#FFE3D2';
  static readonly cat2: string = '#DDF0E6';
  static readonly cat3: string = '#E4E9F7';
  static readonly cat4: string = '#FBE9D8';

  /** 语义色浅底 */
  static readonly okBg: string = '#E9F7F0';
  static readonly okText: string = '#177A53';
  static readonly warnBg: string = '#FDF2E3';
  static readonly warnText: string = '#A9660F';
  static readonly grayBg: string = '#F5F1EC';
}

export class FontSize {
  static readonly h1: number = 26;
  static readonly h2: number = 15;
  static readonly cardTitle: number = 14;
  static readonly body: number = 13;
  static readonly minor: number = 12;
  static readonly tiny: number = 11;
  static readonly priceLg: number = 22;
  static readonly priceSm: number = 16;
  static readonly tag: number = 10.5;
}

export class Space {
  static readonly page: number = 16;
  static readonly cardPad: number = 14;
  static readonly cardGap: number = 10;
}

export class Radius {
  static readonly card: number = 16;
  static readonly btn: number = 14;
  static readonly iconBox: number = 14;
  static readonly tag: number = 6;
  static readonly sheet: number = 18;
}

export class Layout {
  static readonly statusBar: number = 44;
  static readonly navBar: number = 52;
  static readonly tabBar: number = 60;
  static readonly btnHeight: number = 46;
  static readonly screenW: number = 360;
  static readonly screenH: number = 780;
}

/** 按分类序号取缩略块底色，保证 4 个分类颜色稳定不跳。 */
export function categoryBg(index: number): string {
  const list: string[] = [Palette.cat1, Palette.cat2, Palette.cat3, Palette.cat4];
  return list[index % list.length];
}

/** 按分类序号取图标描边色，与底色同色系。 */
export function categoryIconColor(index: number): string {
  const list: string[] = ['#C2592C', '#177A53', '#4A66B8', '#B08246'];
  return list[index % list.length];
}
