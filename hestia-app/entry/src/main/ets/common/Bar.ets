import window from '@ohos.window';
import common from '@ohos.app.ability.common';

/** 状态栏控制。沉浸式页面进页面时设白字，离开时改回深色。 */
export class Bar {
  static set(context: common.Context, bgColor: string, contentColor: string): void {
    window.getLastWindow(context).then((win: window.Window) => {
      win.setWindowSystemBarProperties({
        statusBarColor: bgColor,
        statusBarContentColor: contentColor
      });
    }).catch((err: Object) => {
      // 状态栏设置失败不影响业务，静默忽略
    });
  }

  /** 普通页面：米白底 + 深色字。 */
  static light(context: common.Context): void {
    Bar.set(context, '#FBF8F5', '#1C1917');
  }

  /** 沉浸式页面：主色底 + 白色字。 */
  static brand(context: common.Context): void {
    Bar.set(context, '#F0653A', '#FFFFFF');
  }
}
