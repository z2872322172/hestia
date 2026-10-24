import { FontSize, Layout, Palette, Radius, Space } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import { Nav } from '../common/Nav';
import { RouteName } from '../common/RouteName';
import { Storage } from '../common/Storage';
import { Bar } from '../common/Bar';
import common from '@ohos.app.ability.common';

/**
 * 01 欢迎页。
 *
 * 这里不校验 token 是否有效，只判断"有没有"。过期由首页首个请求的 401 统一兜住，
 * 避免启动时多一次网络往返。
 */
@Component
export struct WelcomePage {
  @State checking: boolean = false;

  aboutToAppear(): void {
    Bar.brand(getContext(this) as common.Context);
  }

  private async enter(): Promise<void> {
    if (this.checking) {
      return;
    }
    this.checking = true;
    const token: string = await Storage.getToken();
    if (token.length > 0) {
      Nav.reset(RouteName.MAIN);
    } else {
      Nav.reset(RouteName.LOGIN);
    }
    this.checking = false;
  }

  build() {
    NavDestination() {
      Stack({ alignContent: Alignment.TopStart }) {
        // 背景装饰圆
        Circle()
          .width(260)
          .height(260)
          .fill('rgba(255,255,255,0.10)')
          .position({ x: 210, y: -70 })
        Circle()
          .width(180)
          .height(180)
          .fill('rgba(255,255,255,0.08)')
          .position({ x: -60, y: 470 })

        Column() {
          Blank()

          // Logo
          Stack() {
            NavIcon({
              path: IconPath.HOUSE_FILLED,
              color: Palette.brand,
              size: 42,
              filled: true
            })
          }
          .width(84)
          .height(84)
          .backgroundColor(Color.White)
          .borderRadius(26)

          Text('Hestia 家政')
            .fontSize(FontSize.h1)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
            .margin({ top: 22 })

          Text('让每一次归家，都干净如初')
            .fontSize(FontSize.body)
            .fontColor('rgba(255,255,255,0.86)')
            .margin({ top: 10 })

          Blank()

          Column() {
            Button(this.checking ? '正在进入…' : '开始使用')
              .width('100%')
              .height(Layout.btnHeight)
              .fontSize(15)
              .fontWeight(FontWeight.Medium)
              .fontColor(Palette.brand)
              .backgroundColor(Color.White)
              .borderRadius(Radius.btn)
              .enabled(!this.checking)
              .onClick(() => {
                this.enter();
              })

            Text('已开通 广州 · 天河')
              .fontSize(FontSize.tiny)
              .fontColor('rgba(255,255,255,0.75)')
              .margin({ top: 14 })

            Text('登录即代表同意《服务协议》与《隐私政策》')
              .fontSize(10)
              .fontColor('rgba(255,255,255,0.55)')
              .margin({ top: 18, bottom: 8 })
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page })
        }
        .width('100%')
        .height('100%')
        .padding({ top: Layout.statusBar })
      }
      .width('100%')
      .height('100%')
      .linearGradient({
        angle: 160,
        colors: [[Palette.brand, 0.0], [Palette.brandDeep, 1.0]]
      })
    }
    .hideTitleBar(true)
  }
}
