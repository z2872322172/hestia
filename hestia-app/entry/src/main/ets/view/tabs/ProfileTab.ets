import { FontSize, Layout, Palette, Radius, Space } from '../../common/Theme';
import { IconPath } from '../../common/IconPath';
import { NavIcon } from '../../components/NavIcon';
import { Account, CustomerProfile } from '../../model/Customer';
import { AuthApi } from '../../api/AuthApi';
import { CustomerApi } from '../../api/CustomerApi';
import { BookingApi } from '../../api/BookingApi';
import { ApiError } from '../../api/ApiClient';
import { PageResult } from '../../model/ApiTypes';
import { BookingSummary } from '../../model/Booking';
import { Storage } from '../../common/Storage';
import { Nav } from '../../common/Nav';
import { AddressParam, RouteName } from '../../common/RouteName';
import { MockData } from '../../mock/MockData';
import { Toast } from '../../common/Toast';

/** 06 我的 Tab。 */
@Component
export struct ProfileTab {
  onSwitchTab: (index: number) => void = () => {
  };

  @State account: Account | null = null;
  @State profile: CustomerProfile | null = null;
  @State counts: number[] = [0, 0, 0, 0];
  @State loading: boolean = true;
  @StorageProp('tabIndex') @Watch('onTabChange') tabIndex: number = 0;

  aboutToAppear(): void {
    this.load();
  }

  onTabChange(): void {
    if (this.tabIndex === 3) {
      this.load();
    }
  }

  private async load(): Promise<void> {
    try {
      this.account = await AuthApi.me();
      this.profile = await CustomerApi.profile();
    } catch (err) {
      if (err instanceof ApiError) {
        // token 失效：ApiClient 已清 token，这里把用户送回登录页
        if (err.code === 'AUTH_TOKEN_EXPIRED' || err.code === 'AUTH_REQUIRED') {
          Nav.reset(RouteName.LOGIN);
          return;
        }
        Toast.show(err.message);
      } else {
        this.account = MockData.account();
      }
    }
    this.loadCounts();
    this.loading = false;
  }

  /** 后端没有聚合统计接口，用 size=1 只取 total，4 个并发。 */
  private async loadCounts(): Promise<void> {
    try {
      const tasks: Promise<PageResult<BookingSummary>>[] = [
        BookingApi.list('PENDING_PAYMENT', 1, 1),
        BookingApi.list('CONFIRMED', 1, 1),
        BookingApi.list('IN_SERVICE', 1, 1),
        BookingApi.list('COMPLETED', 1, 1)
      ];
      const results: PageResult<BookingSummary>[] = await Promise.all(tasks);
      this.counts = [results[0].total, results[1].total, results[2].total, results[3].total];
    } catch (err) {
      this.counts = [0, 0, 0, 0];
    }
  }

  private displayName(): string {
    if (this.account === null) {
      return '未登录';
    }
    return this.account.displayName;
  }

  private phoneText(): string {
    if (this.account === null) {
      return '';
    }
    return this.account.phoneMasked;
  }

  private confirmLogout(): void {
    AlertDialog.show({
      title: '退出登录',
      message: '退出后需要重新输入手机号和密码',
      primaryButton: {
        value: '取消',
        action: () => {
        }
      },
      secondaryButton: {
        value: '退出',
        fontColor: Palette.danger,
        action: () => {
          this.doLogout();
        }
      }
    });
  }

  private async doLogout(): Promise<void> {
    try {
      await AuthApi.logout();
    } catch (err) {
      // 后端退出失败也要清本地，否则用户会卡在已登录态
    }
    await Storage.logout();
    Nav.reset(RouteName.LOGIN);
  }

  @Builder
  countCell(count: number, label: string) {
    Column() {
      Text(count > 99 ? '99+' : count.toString())
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor(Palette.ink)
        .fontFeature('"tnum" on')
      Text(label)
        .fontSize(FontSize.tiny)
        .fontColor(Palette.ink2)
        .margin({ top: 3 })
    }
    .layoutWeight(1)
    .onClick(() => this.onSwitchTab(1))
  }

  @Builder
  menuRow(icon: string, title: string, rightText: string) {
    Row() {
      NavIcon({ path: icon, color: Palette.ink2, size: 19, weight: 1.6 })
      Text(title)
        .fontSize(FontSize.body)
        .fontColor(Palette.ink)
        .margin({ left: 11 })
        .layoutWeight(1)
      if (rightText.length > 0) {
        Text(rightText)
          .fontSize(FontSize.tiny)
          .fontColor(Palette.ink3)
          .margin({ right: 4 })
      }
      NavIcon({ path: IconPath.CHEVRON, color: '#C9C2BA', size: 14, weight: 2 })
    }
    .width('100%')
    .height(50)
    .padding({ left: Space.cardPad, right: Space.cardPad })
  }

  build() {
    Column() {
      // ===== 主色头部 =====
      Column() {
        Row().height(Layout.statusBar)

        Row() {
          Stack() {
            Text(this.displayName().substring(0, 1))
              .fontSize(17)
              .fontWeight(FontWeight.Medium)
              .fontColor(Color.White)
          }
          .width(56)
          .height(56)
          .backgroundColor('rgba(255,255,255,0.28)')
          .borderRadius(28)

          Column() {
            Text(this.displayName())
              .fontSize(17)
              .fontWeight(FontWeight.Bold)
              .fontColor(Color.White)
            Text(this.phoneText())
              .fontSize(FontSize.minor)
              .fontColor('rgba(255,255,255,0.8)')
              .margin({ top: 5 })
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
          .margin({ left: 14 })

          NavIcon({ path: IconPath.SETTING, color: Color.White, size: 20, weight: 1.7 })
            .onClick(() => Toast.show('设置功能开发中'))
        }
        .width('100%')
        .padding({ top: 8, bottom: 26 })
      }
      .width('100%')
      .padding({ left: Space.page, right: Space.page })
      .backgroundColor(Palette.brand)

      Scroll() {
        Column() {
          // 订单数据卡
          Row() {
            this.countCell(this.counts[0], '待支付')
            this.countCell(this.counts[1], '待服务')
            this.countCell(this.counts[2], '服务中')
            this.countCell(this.counts[3], '已完成')
          }
          .width('100%')
          .padding({ top: 18, bottom: 18 })
          .backgroundColor(Palette.surface)
          .borderRadius(Radius.card)
          .margin({ top: -16 })

          // 功能列表
          Column() {
            Column() {
              this.menuRow(IconPath.LOCATION, '地址管理', '')
            }
            .onClick(() => {
              const param: AddressParam = { pickMode: false };
              Nav.push(RouteName.ADDRESS, param);
            })

            Divider().color(Palette.line).margin({ left: Space.cardPad, right: Space.cardPad })

            Column() {
              this.menuRow(IconPath.STAR, '我的评价', '')
            }
            .onClick(() => Toast.show('评价记录请在订单详情中查看'))

            Divider().color(Palette.line).margin({ left: Space.cardPad, right: Space.cardPad })

            Column() {
              this.menuRow(IconPath.COUPON, '优惠券', '暂无可用')
            }
            .onClick(() => Toast.show('优惠券功能开发中'))

            Divider().color(Palette.line).margin({ left: Space.cardPad, right: Space.cardPad })

            Column() {
              this.menuRow(IconPath.SERVICE, '联系客服', '9:00-21:00')
            }
            .onClick(() => Toast.show('客服电话 400-000-0000'))

            Divider().color(Palette.line).margin({ left: Space.cardPad, right: Space.cardPad })

            Column() {
              this.menuRow(IconPath.SETTING, '设置', '')
            }
            .onClick(() => Toast.show('设置功能开发中'))
          }
          .width('100%')
          .backgroundColor(Palette.surface)
          .borderRadius(Radius.card)
          .margin({ top: 12 })

          // 退出登录
          Button('退出登录')
            .width('100%')
            .height(Layout.btnHeight)
            .fontSize(15)
            .fontColor(Palette.danger)
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.btn)
            .margin({ top: 20 })
            .onClick(() => this.confirmLogout())

          Text('Hestia 家政 · v1.0.0')
            .fontSize(10.5)
            .fontColor(Palette.ink3)
            .margin({ top: 20, bottom: 24 })
        }
        .width('100%')
        .padding({ left: Space.page, right: Space.page })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Palette.bg)
  }
}
