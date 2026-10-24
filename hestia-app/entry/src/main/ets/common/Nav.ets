/**
 * 全局导航栈。
 *
 * 为什么用静态单例而不是 @Provide/@Consume：
 * NavDestination 的页面是 builder 动态创建的，@Consume 在部分 API 版本上拿不到祖先的 @Provide，
 * 会在运行时直接崩溃。静态单例没有组件树依赖，最稳。
 */
export class Nav {
  static readonly stack: NavPathStack = new NavPathStack();

  /** 入栈。param 传 {} 而不是 null，ArkTS 严格模式下 null 不匹配 Object。 */
  static push(name: string, param: Object): void {
    Nav.stack.pushPathByName(name, param);
  }

  /** 返回上一页。 */
  static back(): void {
    Nav.stack.pop();
  }

  /** 替换当前页（支付成功后不让用户退回支付页）。 */
  static replace(name: string, param: Object): void {
    Nav.stack.replacePathByName(name, param);
  }

  /** 清空整个栈后进入指定页（登录 / 退出登录）。 */
  static reset(name: string): void {
    Nav.stack.clear();
    Nav.stack.pushPathByName(name, new Object());
  }
}
