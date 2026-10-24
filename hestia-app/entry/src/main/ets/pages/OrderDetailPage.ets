import pasteboard from '@ohos.pasteboard';
import common from '@ohos.app.ability.common';
import { FontSize, Layout, Palette, Radius, Space } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import { PriceText } from '../components/PriceText';
import { StatusTag } from '../components/StatusTag';
import { StepTimeline } from '../components/StepTimeline';
import { BookingDetail, Fulfillment, TimelineStep } from '../model/Booking';
import { BookingStatus, orderStatusHeadline } from '../model/Enums';
import { BookingApi } from '../api/BookingApi';
import { ApiClient } from '../api/ApiClient';
import { MockData } from '../mock/MockData';
import { Nav } from '../common/Nav';
import { OrderDetailParam, PaymentParam, ReviewParam, RouteName } from '../common/RouteName';
import { Format } from '../common/Format';
import { Toast } from '../common/Toast';
import { Bar } from '../common/Bar';

/**
 * 11 订单详情。
 *
 * 6 段进度时间线的数据来自 GET /customer/bookings/{orderNo}/fulfillment
 * （departedAt / checkInAt / startedAt / finishedAt / customerConfirmedAt），
 * 不是本地假动画。
 */
@Component
export struct OrderDetailPage {
  param: OrderDetailParam = { orderNo: '' };

  @State detail: BookingDetail | null = null;
  @State fulfillment: Fulfillment | null = null;
  @State loading: boolean = true;

  aboutToAppear(): void {
    Bar.brand(getContext(this) as common.Context);
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    try {
      this.detail = await BookingApi.detail(this.param.orderNo);
    } catch (err) {
      if (ApiClient.isBusinessError(err as Object)) {
        Toast.show(ApiClient.messageOf(err as Object));
      } else {
        this.detail = MockData.bookingDetail(this.param.orderNo);
      }
    }
    try {
      this.fulfillment = await BookingApi.fulfillment(this.param.orderNo);
    } catch (err) {
      // 未派单时该接口 404，属正常，时间线只显示前两段
      this.fulfillment = null;
    }
    this.loading = false;
  }

  private timeline(): TimelineStep[] {
    if (this.detail === null) {
      return [];
    }
    const departed: string = this.fulfillment !== null ? this.fulfillment.departedAt : '';
    const checkIn: string = this.fulfillment !== null ? this.fulfillment.checkInAt : '';
    const finished: string = this.fulfillment !== null ? this.fulfillment.finishedAt : '';
    const confirmed: string = this.fulfillment !== null ? this.fulfillment.customerConfirmedAt : '';

    const steps: TimelineStep[] = [];
    steps.push({ title: '已下单', time: this.detail.createdAt, state: 'done' });
    steps.push({
      title: '支付成功',
      time: this.detail.paidAt,
      state: this.hasText(this.detail.paidAt) ? 'done' : 'todo'
    });
    steps.push({
      title: '服务人员出发',
      time: departed,
      state: this.hasText(departed) ? 'done' : 'todo'
    });
    steps.push({
      title: '到达并签到',
      time: checkIn,
      state: this.hasText(checkIn) ? 'done' : 'todo'
    });
    steps.push({
      title: '服务完成',
      time: finished,
      state: this.hasText(finished) ? 'done' : 'todo'
    });
    steps.push({
      title: '确认完成',
      time: confirmed,
      state: this.hasText(confirmed) ? 'done' : 'todo'
    });

    // 第一个未完成的步骤标为进行中
    for (let i = 0; i < steps.length; i++) {
      if (steps[i].state === 'todo') {
        steps[i].state = 'now';
        break;
      }
    }
    return steps;
  }

  private hasText(value: string): boolean {
    return value !== undefined && value !== null && value.length > 0;
  }

  private copyOrderNo(): void {
    if (this.detail === null) {
      return;
    }
    const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, this.detail.orderNo);
    pasteboard.getSystemPasteboard().setData(data).then(() => {
      Toast.show('订单号已复制');
    }).catch(() => {
      Toast.show('复制失败');
    });
  }

  private async confirmDone(): Promise<void> {
    try {
      await BookingApi.confirm(this.param.orderNo);
      Toast.show('已确认完成，现在可以评价了');
      this.load();
    } catch (err) {
      Toast.show(ApiClient.messageOf(err as Object));
    }
  }

  @Builder
  infoRow(label: string, value: string) {
    Row() {
      Text(label)
        .fontSize(FontSize.minor)
        .fontColor(Palette.ink3)
      Blank()
      Text(value)
        .fontSize(FontSize.minor)
        .fontColor(Palette.ink)
        .maxLines(2)
        .textAlign(TextAlign.End)
        .layoutWeight(1)
        .margin({ left: 16 })
    }
    .width('100%')
    .padding({ top: 7, bottom: 7 })
    .alignItems(VerticalAlign.Top)
  }

  @Builder
  costRow(label: string, amount: number, highlight: boolean) {
    Row() {
      Text(label)
        .fontSize(FontSize.minor)
        .fontColor(highlight ? Palette.ink : Palette.ink2)
        .fontWeight(highlight ? FontWeight.Medium : FontWeight.Normal)
      Blank()
      Text((amount < 0 ? '-¥' : '¥') + Format.money(Math.abs(amount)))
        .fontSize(FontSize.minor)
        .fontColor(highlight ? Palette.brand : Palette.ink)
        .fontWeight(highlight ? FontWeight.Medium : FontWeight.Normal)
        .fontFeature('"tnum" on')
    }
    .width('100%')
    .padding({ top: 6, bottom: 6 })
  }

  build() {
    NavDestination() {
      Column() {
        if (this.loading) {
          Column() {
            LoadingProgress()
              .width(32)
              .height(32)
              .color(Palette.brand)
          }
          .width('100%')
          .height('100%')
          .justifyContent(FlexAlign.Center)
        } else if (this.detail === null) {
          Column() {
            Text('订单不存在')
              .fontSize(FontSize.body)
              .fontColor(Palette.ink2)
            Button('返回')
              .fontSize(FontSize.body)
              .fontColor(Palette.brand)
              .backgroundColor(Palette.brandXLight)
              .height(38)
              .borderRadius(Radius.btn)
              .margin({ top: 16 })
              .onClick(() => Nav.back())
          }
          .width('100%')
          .height('100%')
          .justifyContent(FlexAlign.Center)
        } else {
          Scroll() {
            Column() {
              // ===== 沉浸式状态头 =====
              Column() {
                Row().height(Layout.statusBar)

                Row() {
                  Row() {
                    NavIcon({ path: IconPath.BACK, color: Color.White, size: 22, weight: 2 })
                  }
                  .width(40)
                  .height(40)
                  .onClick(() => Nav.back())

                  Text('订单详情')
                    .fontSize(16)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(Color.White)
                    .layoutWeight(1)
                    .textAlign(TextAlign.Center)

                  Row().width(40).height(40)
                }
                .width('100%')
                .height(Layout.navBar)
                .padding({ left: 8, right: 8 })

                Column() {
                  Text(this.detail.orderStatus === BookingStatus.CANCELED ? '已取消' :
                    this.detail.orderStatus === BookingStatus.COMPLETED ? '已完成' :
                      this.detail.orderStatus === BookingStatus.PENDING_PAYMENT ? '待支付' : '进行中')
                    .fontSize(22)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(Color.White)
                  Text(orderStatusHeadline(this.detail.orderStatus))
                    .fontSize(FontSize.minor)
                    .fontColor('rgba(255,255,255,0.85)')
                    .margin({ top: 6 })
                }
                .width('100%')
                .alignItems(HorizontalAlign.Start)
                .padding({ left: Space.page, right: Space.page, top: 6, bottom: 26 })
              }
              .width('100%')
              .backgroundColor(Palette.brand)

              // ===== 服务人员 =====
              Row() {
                Stack() {
                  NavIcon({ path: IconPath.PROFILE, color: Palette.brand, size: 22, weight: 1.6 })
                }
                .width(44)
                .height(44)
                .backgroundColor(Palette.brandXLight)
                .borderRadius(22)

                Column() {
                  Text(this.detail.currentWorkerId > 0 ? '已安排服务人员' : '正在为你安排服务人员')
                    .fontSize(FontSize.body)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(Palette.ink)
                  Text(this.detail.currentWorkerId > 0 ?
                    '出发后会有短信通知，也可在此页查看进度' : '通常会在服务前 24 小时内完成派单')
                    .fontSize(FontSize.tiny)
                    .fontColor(Palette.ink3)
                    .margin({ top: 4 })
                }
                .alignItems(HorizontalAlign.Start)
                .layoutWeight(1)
                .margin({ left: 12 })
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: -16, left: Space.page, right: Space.page })
              .width('calc(100% - 32vp)')

              // ===== 进度时间线 =====
              Column() {
                Text('服务进度')
                  .fontSize(FontSize.h2)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(Palette.ink)
                  .width('100%')
                  .margin({ bottom: 14 })

                StepTimeline({ steps: this.timeline() })
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10, left: Space.page, right: Space.page })
              .width('calc(100% - 32vp)')
              .alignItems(HorizontalAlign.Start)

              // ===== 服务信息 =====
              Column() {
                Row() {
                  Text(this.detail.serviceName)
                    .fontSize(FontSize.cardTitle)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(Palette.ink)
                    .layoutWeight(1)
                  StatusTag({ status: this.detail.orderStatus })
                }
                .width('100%')
                .margin({ bottom: 8 })

                Divider().color(Palette.line).margin({ top: 4, bottom: 4 })

                this.infoRow('预约时间', this.detail.appointmentDate + ' ' +
                  (this.detail.serviceWindowName !== undefined && this.detail.serviceWindowName !== null ?
                    this.detail.serviceWindowName : ''))
                this.infoRow('服务地址', this.detail.detailAddress)
                this.infoRow('联系人', this.detail.contactName + ' ' + this.detail.contactPhone)
                if (this.hasText(this.detail.serviceAreaName)) {
                  this.infoRow('服务片区', this.detail.serviceAreaName)
                }
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10, left: Space.page, right: Space.page })
              .width('calc(100% - 32vp)')
              .alignItems(HorizontalAlign.Start)

              // ===== 费用明细 =====
              Column() {
                Text('费用明细')
                  .fontSize(FontSize.h2)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(Palette.ink)
                  .width('100%')
                  .margin({ bottom: 4 })

                this.costRow('服务费', this.detail.serviceAmount, false)
                if (this.detail.addonAmount > 0) {
                  this.costRow('加项费用', this.detail.addonAmount, false)
                }
                if (this.detail.materialAmount > 0) {
                  this.costRow('材料费', this.detail.materialAmount, false)
                }
                if (this.detail.travelFee > 0) {
                  this.costRow('上门费', this.detail.travelFee, false)
                }
                if (this.detail.discountAmount > 0) {
                  this.costRow('优惠', -this.detail.discountAmount, false)
                }
                Divider().color(Palette.line).margin({ top: 4, bottom: 4 })
                this.costRow('应付金额', this.detail.payableAmount, true)
                if (this.detail.paidAmount > 0) {
                  this.costRow('已付金额', this.detail.paidAmount, false)
                }
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10, left: Space.page, right: Space.page })
              .width('calc(100% - 32vp)')
              .alignItems(HorizontalAlign.Start)

              // ===== 订单信息 =====
              Column() {
                Row() {
                  Text('订单信息')
                    .fontSize(FontSize.h2)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(Palette.ink)
                    .layoutWeight(1)
                  Row() {
                    NavIcon({ path: IconPath.COPY, color: Palette.ink3, size: 13, weight: 1.8 })
                    Text('复制')
                      .fontSize(FontSize.tiny)
                      .fontColor(Palette.ink3)
                      .margin({ left: 3 })
                  }
                  .onClick(() => this.copyOrderNo())
                }
                .width('100%')
                .margin({ bottom: 8 })

                this.infoRow('订单号', this.detail.orderNo)
                this.infoRow('下单时间', Format.dateTime(this.detail.createdAt))
                if (this.hasText(this.detail.cancelReasonText)) {
                  this.infoRow('取消原因', this.detail.cancelReasonText)
                }
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10, left: Space.page, right: Space.page })
              .width('calc(100% - 32vp)')
              .alignItems(HorizontalAlign.Start)

              Row().height(24)
            }
            .width('100%')
          }
          .layoutWeight(1)
          .scrollBar(BarState.Off)

          // ===== 底部操作 =====
          Row({ space: 10 }) {
            if (this.detail.orderStatus === BookingStatus.PENDING_PAYMENT) {
              Button('去支付')
                .layoutWeight(1)
                .height(Layout.btnHeight)
                .fontSize(15)
                .fontWeight(FontWeight.Medium)
                .fontColor(Color.White)
                .backgroundColor(Palette.brand)
                .borderRadius(Radius.btn)
                .onClick(() => {
                  const param: PaymentParam = {
                    orderNo: this.detail!.orderNo,
                    payableAmount: this.detail!.payableAmount,
                    deadline: this.detail!.paymentDeadline
                  };
                  Nav.push(RouteName.PAYMENT, param);
                })
            } else if (this.detail.orderStatus === BookingStatus.PENDING_CONFIRM) {
              Button('确认完成')
                .layoutWeight(1)
                .height(Layout.btnHeight)
                .fontSize(15)
                .fontWeight(FontWeight.Medium)
                .fontColor(Color.White)
                .backgroundColor(Palette.brand)
                .borderRadius(Radius.btn)
                .onClick(() => this.confirmDone())
            } else if (this.detail.orderStatus === BookingStatus.COMPLETED) {
              Button('评价服务')
                .layoutWeight(1)
                .height(Layout.btnHeight)
                .fontSize(15)
                .fontWeight(FontWeight.Medium)
                .fontColor(Color.White)
                .backgroundColor(Palette.brand)
                .borderRadius(Radius.btn)
                .onClick(() => {
                  const param: ReviewParam = {
                    orderNo: this.detail!.orderNo,
                    serviceName: this.detail!.serviceName
                  };
                  Nav.push(RouteName.REVIEW, param);
                })
            } else {
              Button('联系客服')
                .layoutWeight(1)
                .height(Layout.btnHeight)
                .fontSize(15)
                .fontWeight(FontWeight.Medium)
                .fontColor(Palette.brand)
                .backgroundColor(Palette.brandXLight)
                .borderRadius(Radius.btn)
                .onClick(() => Toast.show('客服电话 400-000-0000'))
            }
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page, top: 10, bottom: 14 })
          .backgroundColor(Palette.surface)
          .border({ width: { top: 1 }, color: Palette.line })
        }
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Palette.bg)
    }
    .hideTitleBar(true)
  }
}
