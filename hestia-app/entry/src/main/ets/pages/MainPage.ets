import { Layout, Palette } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import { HomeTab } from '../view/tabs/HomeTab';
import { OrderTab } from '../view/tabs/OrderTab';
import { MessageTab } from '../view/tabs/MessageTab';
import { ProfileTab } from '../view/tabs/ProfileTab';
import { MessageApi } from '../api/MessageApi';
import { Bar } from '../common/Bar';
import common from '@ohos.app.ability.common';

/**
 * Tab 容器（03–06）。
 *
 * 用自定义 tabBar 而不是默认样式：默认 TabBar 的图标尺寸和间距跟设计稿对不上。
 * 未读数走 AppStorage，MessageTab 拉完数据后回写，这里只负责显示红点。
 */
@Component
export struct MainPage {
  @State currentIndex: number = 0;
  @State unreadCount: number = 0;
  private controller: TabsController = new TabsController();

  aboutToAppear(): void {
    Bar.brand(getContext(this) as common.Context);
    this.refreshUnread();
  }

  private async refreshUnread(): Promise<void> {
    try {
      this.unreadCount = await MessageApi.unreadCount();
      AppStorage.setOrCreate('unreadCount', this.unreadCount);
    } catch (err) {
      this.unreadCount = 0;
    }
  }

  @Builder
  tabBuilder(index: number, title: string, icon: string) {
    Column() {
      Stack({ alignContent: Alignment.TopEnd }) {
        NavIcon({
          path: icon,
          color: this.currentIndex === index ? Palette.brand : '#A8A19A',
          size: 22,
          weight: 1.8
        })
        if (index === 2 && this.unreadCount > 0) {
          Text(this.unreadCount > 99 ? '99+' : this.unreadCount.toString())
            .fontSize(9)
            .fontColor(Color.White)
            .backgroundColor(Palette.danger)
            .borderRadius(7)
            .padding({ left: 4, right: 4, top: 1, bottom: 1 })
            .margin({ right: -8, top: -5 })
        }
      }
      .width(30)
      .height(24)

      Text(title)
        .fontSize(10.5)
        .fontColor(this.currentIndex === index ? Palette.brand : '#A8A19A')
        .fontWeight(this.currentIndex === index ? FontWeight.Medium : FontWeight.Normal)
        .margin({ top: 3 })
    }
    .width('100%')
    .height(Layout.tabBar)
    .justifyContent(FlexAlign.Center)
  }

  build() {
    NavDestination() {
      Tabs({ barPosition: BarPosition.End, controller: this.controller }) {
        TabContent() {
          HomeTab({
            onOpenMessage: () => {
              this.controller.changeIndex(2);
            }
          })
        }
        .tabBar(this.tabBuilder(0, '首页', IconPath.HOME))

        TabContent() {
          OrderTab()
        }
        .tabBar(this.tabBuilder(1, '订单', IconPath.ORDER))

        TabContent() {
          MessageTab()
        }
        .tabBar(this.tabBuilder(2, '消息', IconPath.MESSAGE))

        TabContent() {
          ProfileTab({
            onSwitchTab: (index: number) => {
              this.controller.changeIndex(index);
            }
          })
        }
        .tabBar(this.tabBuilder(3, '我的', IconPath.PROFILE))
      }
      .barHeight(Layout.tabBar)
      .barBackgroundColor(Palette.surface)
      .scrollable(false)
      .backgroundColor(Palette.bg)
      .onChange((index: number) => {
        this.currentIndex = index;
        AppStorage.setOrCreate('tabIndex', index);
        // 首页与我的页是沉浸式主色头部，状态栏用白字
        if (index === 0 || index === 3) {
          Bar.brand(getContext(this) as common.Context);
        } else {
          Bar.light(getContext(this) as common.Context);
        }
        if (index === 2) {
          this.refreshUnread();
        }
      })
    }
    .hideTitleBar(true)
    .backgroundColor(Palette.bg)
    .onShown(() => {
      // 从详情页返回时恢复状态栏配色
      if (this.currentIndex === 0 || this.currentIndex === 3) {
        Bar.brand(getContext(this) as common.Context);
      } else {
        Bar.light(getContext(this) as common.Context);
      }
    })
  }
}
