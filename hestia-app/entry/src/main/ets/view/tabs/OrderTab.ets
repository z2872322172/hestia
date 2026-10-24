import { FontSize, Palette, Space } from '../../common/Theme';
import { OrderCard } from '../../components/OrderCard';
import { EmptyState } from '../../components/EmptyState';
import { IconPath } from '../../common/IconPath';
import { BookingSummary } from '../../model/Booking';
import { BookingStatus, ORDER_TAB_GROUPS, OrderTabGroup } from '../../model/Enums';
import { BookingApi } from '../../api/BookingApi';
import { ApiClient } from '../../api/ApiClient';
import { MockData } from '../../mock/MockData';
import { Nav } from '../../common/Nav';
import {
  OrderDetailParam,
  PaymentParam,
  ReviewParam,
  RouteName,
  ServiceListParam
} from '../../common/RouteName';
import { Toast } from '../../common/Toast';

/** 04 订单 Tab。状态分组见 model/Enums 的 ORDER_TAB_GROUPS。 */
@Component
export struct OrderTab {
  @State activeTab: number = 0;
  @State list: BookingSummary[] = [];
  @State loading: boolean = true;
  @State refreshing: boolean = false;
  @State degraded: boolean = false;
  @StorageProp('tabIndex') @Watch('onTabChange') tabIndex: number = 0;

  aboutToAppear(): void {
    this.load();
  }

  onTabChange(): void {
    if (this.tabIndex === 1) {
      this.load();
    }
  }

  private async load(): Promise<void> {
    this.loading = this.list.length === 0;
    try {
      const group: OrderTabGroup = ORDER_TAB_GROUPS[this.activeTab];
      this.list = await BookingApi.listByStatuses(group.statuses, 1, 20);
      this.degraded = false;
    } catch (err) {
      if (ApiClient.isBusinessError(err as Object)) {
        Toast.show(ApiClient.messageOf(err as Object));
      } else {
        this.list = this.mockByGroup();
        this.degraded = true;
      }
    }
    this.loading = false;
    this.refreshing = false;
  }

  /** Mock 兜底时按当前分组过滤，保证演示效果与真实分组一致。 */
  private mockByGroup(): BookingSummary[] {
    const all: BookingSummary[] = MockData.bookings();
    const group: OrderTabGroup = ORDER_TAB_GROUPS[this.activeTab];
    if (group.statuses.length === 0) {
      return all;
    }
    const filtered: BookingSummary[] = [];
    for (let i = 0; i < all.length; i++) {
      for (let j = 0; j < group.statuses.length; j++) {
        if (all[i].orderStatus === group.statuses[j]) {
          filtered.push(all[i]);
        }
      }
    }
    return filtered;
  }

  private openDetail(orderNo: string): void {
    const param: OrderDetailParam = { orderNo: orderNo };
    Nav.push(RouteName.ORDER_DETAIL, param);
  }

  private handleAction(action: string, orderNo: string): void {
    if (action === 'detail') {
      this.openDetail(orderNo);
      return;
    }
    if (action === 'pay') {
      let amount: number = 0;
      for (let i = 0; i < this.list.length; i++) {
        if (this.list[i].orderNo === orderNo) {
          amount = this.list[i].payableAmount;
        }
      }
      const param: PaymentParam = { orderNo: orderNo, payableAmount: amount, deadline: '' };
      Nav.push(RouteName.PAYMENT, param);
      return;
    }
    if (action === 'review') {
      let serviceName: string = '';
      for (let i = 0; i < this.list.length; i++) {
        if (this.list[i].orderNo === orderNo) {
          serviceName = this.list[i].serviceName;
        }
      }
      const param: ReviewParam = { orderNo: orderNo, serviceName: serviceName };
      Nav.push(RouteName.REVIEW, param);
      return;
    }
    if (action === 'reschedule') {
      this.openDetail(orderNo);
      return;
    }
    if (action === 'rebook') {
      const param: ServiceListParam = { categoryNo: '', title: '全部服务' };
      Nav.push(RouteName.SERVICE_LIST, param);
      return;
    }
    if (action === 'cancel') {
      this.confirmCancel(orderNo);
      return;
    }
    if (action === 'confirm') {
      this.confirmDone(orderNo);
    }
  }

  private confirmCancel(orderNo: string): void {
    AlertDialog.show({
      title: '取消订单',
      message: '待支付订单可免费取消；已支付订单将按服务快照计算违约金。确定取消吗？',
      primaryButton: {
        value: '再想想',
        action: () => {
        }
      },
      secondaryButton: {
        value: '确定取消',
        fontColor: Palette.danger,
        action: () => {
          this.doCancel(orderNo);
        }
      }
    });
  }

  private async doCancel(orderNo: string): Promise<void> {
    try {
      await BookingApi.cancel(orderNo, 'CUSTOMER_CANCEL', '用户主动取消');
      Toast.show('订单已取消');
      this.load();
    } catch (err) {
      Toast.show(ApiClient.messageOf(err as Object));
      this.load();
    }
  }

  private async confirmDone(orderNo: string): Promise<void> {
    try {
      await BookingApi.confirm(orderNo);
      Toast.show('已确认完成，现在可以评价了');
      this.load();
    } catch (err) {
      Toast.show(ApiClient.messageOf(err as Object));
    }
  }

  @Builder
  statusTab(group: OrderTabGroup, index: number) {
    Column() {
      Text(group.label)
        .fontSize(13.5)
        .fontWeight(this.activeTab === index ? FontWeight.Medium : FontWeight.Normal)
        .fontColor(this.activeTab === index ? Palette.brand : Palette.ink2)
      Row()
        .width(18)
        .height(2)
        .backgroundColor(this.activeTab === index ? Palette.brand : Color.Transparent)
        .borderRadius(1)
        .margin({ top: 6 })
    }
    .padding({ left: 4, right: 4 })
    .onClick(() => {
      if (this.activeTab !== index) {
        this.activeTab = index;
        this.list = [];
        this.load();
      }
    })
  }

  build() {
    Column() {
      Row().height(44)

      Row() {
        Text('我的订单')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(Palette.ink)
        Blank()
        if (this.degraded) {
          Text('演示数据')
            .fontSize(10.5)
            .fontColor(Palette.warnText)
            .backgroundColor(Palette.warnBg)
            .borderRadius(6)
            .padding({ left: 8, right: 8, top: 3, bottom: 3 })
        }
      }
      .width('100%')
      .padding({ left: Space.page, right: Space.page, top: 6, bottom: 4 })

      // 状态分组
      Row() {
        ForEach(ORDER_TAB_GROUPS, (group: OrderTabGroup, index: number) => {
          this.statusTab(group, index)
        }, (group: OrderTabGroup) => group.label)
      }
      .width('100%')
      .padding({ left: Space.page, right: Space.page, top: 10, bottom: 10 })
      .justifyContent(FlexAlign.SpaceBetween)

      Refresh({ refreshing: $$this.refreshing }) {
        if (this.loading) {
          Column() {
            LoadingProgress()
              .width(32)
              .height(32)
              .color(Palette.brand)
          }
          .width('100%')
          .height(240)
          .justifyContent(FlexAlign.Center)
        } else if (this.list.length === 0) {
          EmptyState({
            title: '还没有相关订单',
            desc: '下单后可在这里查看服务进度',
            actionText: '去逛逛',
            iconPath: IconPath.ORDER,
            onAction: () => {
              const param: ServiceListParam = { categoryNo: '', title: '全部服务' };
              Nav.push(RouteName.SERVICE_LIST, param);
            }
          })
        } else {
          List({ space: 10 }) {
            ForEach(this.list, (item: BookingSummary) => {
              ListItem() {
                OrderCard({
                  item: item,
                  onAction: (action: string, orderNo: string) => {
                    this.handleAction(action, orderNo);
                  },
                  onTap: () => {
                    this.openDetail(item.orderNo);
                  }
                })
              }
            }, (item: BookingSummary) => item.orderNo + item.orderStatus)

            ListItem() {
              Row().height(16)
            }
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page })
          .scrollBar(BarState.Off)
          .layoutWeight(1)
        }
      }
      .onRefreshing(() => {
        this.load();
      })
      .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Palette.bg)
  }
}
