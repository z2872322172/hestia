import { Palette } from '../common/Theme';
import { Nav } from '../common/Nav';
import {
  AddressParam,
  BookingParam,
  OrderDetailParam,
  PaymentParam,
  ReviewParam,
  RouteName,
  ServiceDetailParam,
  ServiceListParam
} from '../common/RouteName';
import { WelcomePage } from './WelcomePage';
import { LoginPage } from './LoginPage';
import { MainPage } from './MainPage';
import { ServiceListPage } from './ServiceListPage';
import { ServiceDetailPage } from './ServiceDetailPage';
import { BookingPage } from './BookingPage';
import { PaymentPage } from './PaymentPage';
import { OrderDetailPage } from './OrderDetailPage';
import { ReviewPage } from './ReviewPage';
import { AddressPage } from './AddressPage';

/**
 * 应用唯一入口。
 *
 * 全局只有一个 @Entry，其余页面都是 @Component + NavDestination，
 * 通过 Nav 里的静态 NavPathStack 跳转（不用 @Provide/@Consume，避免动态创建的
 * NavDestination 拿不到祖先状态而崩溃）。
 *
 * 根内容刻意只放一个品牌色占位，真正的首屏由 onAppear 推入 WelcomePage：
 * 这样栈里只有业务页面，在首页按返回键会直接退出应用，而不是退回欢迎页。
 */
@Entry
@Component
struct Index {
  @Builder
  routeMap(name: string, param: Object) {
    if (name === RouteName.WELCOME) {
      WelcomePage()
    } else if (name === RouteName.MAIN) {
      MainPage()
    } else if (name === RouteName.LOGIN) {
      LoginPage()
    } else if (name === RouteName.SERVICE_LIST) {
      ServiceListPage({ param: param as ServiceListParam })
    } else if (name === RouteName.SERVICE_DETAIL) {
      ServiceDetailPage({ param: param as ServiceDetailParam })
    } else if (name === RouteName.BOOKING) {
      BookingPage({ param: param as BookingParam })
    } else if (name === RouteName.PAYMENT) {
      PaymentPage({ param: param as PaymentParam })
    } else if (name === RouteName.ORDER_DETAIL) {
      OrderDetailPage({ param: param as OrderDetailParam })
    } else if (name === RouteName.REVIEW) {
      ReviewPage({ param: param as ReviewParam })
    } else if (name === RouteName.ADDRESS) {
      AddressPage({ param: param as AddressParam })
    }
  }

  build() {
    Navigation(Nav.stack) {
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor(Palette.brand)
        .onAppear(() => {
          if (Nav.stack.size() === 0) {
            Nav.push(RouteName.WELCOME, new Object());
          }
        })
    }
    .hideTitleBar(true)
    .mode(NavigationMode.Stack)
    .navDestination(this.routeMap)
    .backgroundColor(Palette.bg)
  }
}
