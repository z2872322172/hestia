import { FontSize, Palette, Radius, Space } from '../common/Theme';
import { BookingSummary } from '../model/Booking';
import { BookingStatus } from '../model/Enums';
import { Format } from '../common/Format';
import { StatusTag } from './StatusTag';
import { PriceText } from './PriceText';

/**
 * 订单卡片。
 *
 * 按钮严格按后端状态机渲染：BookingStatus.cancellable() 只允许
 * PENDING_PAYMENT 和 CONFIRMED，EN_ROUTE 之后不能取消，
 * 所以那些状态不显示"取消订单"，避免用户点了必然 409。
 */
@Component
export struct OrderCard {
  @Prop item: BookingSummary;
  onAction: (action: string, orderNo: string) => void = () => {
  };
  onTap: () => void = () => {
  };

  @Builder
  chipAction(text: string, primary: boolean, action: string) {
    Text(text)
      .fontSize(12.5)
      .fontColor(primary ? Color.White : Palette.ink)
      .backgroundColor(primary ? Palette.brand : Palette.surface)
      .border({
        width: primary ? 0 : 1,
        color: Palette.line,
        style: BorderStyle.Solid
      })
      .borderRadius(10)
      .height(32)
      .padding({ left: 13, right: 13 })
      .textAlign(TextAlign.Center)
      .onClick(() => this.onAction(action, this.item.orderNo))
  }

  private appointmentText(): string {
    if (this.item.appointmentStart === undefined || this.item.appointmentStart === null ||
      this.item.appointmentStart.length === 0) {
      return this.item.appointmentDate;
    }
    return Format.dateTime(this.item.appointmentStart);
  }

  build() {
    Column() {
      Row() {
        Text(this.item.serviceName)
          .fontSize(FontSize.cardTitle)
          .fontWeight(FontWeight.Medium)
          .fontColor(Palette.ink)
          .layoutWeight(1)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        StatusTag({ status: this.item.orderStatus })
          .margin({ left: 8 })
      }
      .width('100%')

      Row() {
        Text('预约时间')
          .fontSize(FontSize.minor)
          .fontColor(Palette.ink3)
        Text(this.appointmentText())
          .fontSize(FontSize.minor)
          .fontColor(Palette.ink2)
          .margin({ left: 8 })
        Blank()
        Text('订单号 ' + this.item.orderNo)
          .fontSize(FontSize.tiny)
          .fontColor(Palette.ink3)
      }
      .width('100%')
      .margin({ top: 8 })

      Divider()
        .color(Palette.line)
        .margin({ top: 11, bottom: 11 })

      Row() {
        PriceText({ amount: this.item.payableAmount })
        Blank()
        Row({ space: 8 }) {
          if (this.item.orderStatus === BookingStatus.PENDING_PAYMENT) {
            this.chipAction('取消订单', false, 'cancel')
            this.chipAction('去支付', true, 'pay')
          } else if (this.item.orderStatus === BookingStatus.CONFIRMED) {
            this.chipAction('取消订单', false, 'cancel')
            this.chipAction('修改时间', true, 'reschedule')
          } else if (this.item.orderStatus === BookingStatus.EN_ROUTE ||
            this.item.orderStatus === BookingStatus.IN_SERVICE) {
            this.chipAction('查看进度', true, 'detail')
          } else if (this.item.orderStatus === BookingStatus.PENDING_CONFIRM) {
            this.chipAction('确认完成', true, 'confirm')
          } else if (this.item.orderStatus === BookingStatus.COMPLETED) {
            this.chipAction('再次预约', false, 'rebook')
            this.chipAction('评价', true, 'review')
          } else {
            this.chipAction('再次预约', true, 'rebook')
          }
        }
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .padding(Space.cardPad)
    .backgroundColor(Palette.surface)
    .borderRadius(Radius.card)
    .onClick(() => this.onTap())
  }
}
