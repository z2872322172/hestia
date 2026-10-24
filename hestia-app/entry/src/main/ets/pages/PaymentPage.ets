import util from '@ohos.util';
import common from '@ohos.app.ability.common';
import { FontSize, Layout, Palette, Radius, Space } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import { PriceText } from '../components/PriceText';
import { PaymentApi, PaymentCreated } from '../api/PaymentApi';
import { ApiClient } from '../api/ApiClient';
import { Nav } from '../common/Nav';
import { OrderDetailParam, PaymentParam, RouteName } from '../common/RouteName';
import { Format } from '../common/Format';
import { Toast } from '../common/Toast';
import { Bar } from '../common/Bar';

/** 支付方式选项。value 只用于界面展示，提交时统一传 MOCK。 */
interface PayMethod {
  code: string;
  name: string;
  desc: string;
}

const PAY_METHODS: PayMethod[] = [
  { code: 'wechat', name: '微信支付', desc: '推荐使用' },
  { code: 'alipay', name: '支付宝', desc: '' },
  { code: 'balance', name: '余额支付', desc: '演示环境不可用' }
];

/** 10 支付。 */
@Component
export struct PaymentPage {
  param: PaymentParam = { orderNo: '', payableAmount: 0, deadline: '' };

  @State methodCode: string = 'wechat';
  @State countdown: number = 900;
  @State paying: boolean = false;
  @State paid: boolean = false;
  @State paymentNo: string = '';

  private timerId: number = -1;
  private idemKey: string = util.generateRandomUUID(true);

  aboutToAppear(): void {
    Bar.light(getContext(this) as common.Context);
    this.startCountdown();
  }

  aboutToDisappear(): void {
    // 必须清定时器，否则页面销毁后还在跑
    if (this.timerId > 0) {
      clearInterval(this.timerId);
      this.timerId = -1;
    }
  }

  private startCountdown(): void {
    let deadlineMs: number = 0;
    const parsed: Date | null = Format.parseDateTime(this.param.deadline);
    if (parsed !== null) {
      deadlineMs = parsed.getTime();
    } else {
      // 后端没给截止时间时按 15 分钟兜底（与 application.yml 的 payment-timeout-minutes 一致）
      deadlineMs = Date.now() + 15 * 60 * 1000;
    }
    const update = () => {
      const left: number = Math.floor((deadlineMs - Date.now()) / 1000);
      this.countdown = left > 0 ? left : 0;
      if (this.countdown === 0 && this.timerId > 0) {
        clearInterval(this.timerId);
        this.timerId = -1;
      }
    };
    update();
    this.timerId = setInterval(update, 1000);
  }

  private async pay(): Promise<void> {
    if (this.paying || this.paid) {
      return;
    }
    if (this.countdown === 0) {
      Toast.show('支付已超时，请重新下单');
      return;
    }
    this.paying = true;
    try {
      // 无论界面选哪个，后端首版只认 MOCK
      const created: PaymentCreated = await PaymentApi.pay(
        this.param.orderNo, PaymentApi.CHANNEL_MOCK, this.idemKey);
      this.paymentNo = created.paymentNo;
      this.paid = true;
      this.paying = false;
    } catch (err) {
      this.paying = false;
      if (ApiClient.isBusinessError(err as Object)) {
        Toast.show(ApiClient.messageOf(err as Object));
      } else {
        Toast.show('网络异常，支付未完成，请稍后重试');
      }
    }
  }

  private goOrder(): void {
    const param: OrderDetailParam = { orderNo: this.param.orderNo };
    Nav.replace(RouteName.ORDER_DETAIL, param);
  }

  @Builder
  methodRow(item: PayMethod) {
    Row() {
      Stack() {
        if (this.methodCode === item.code) {
          NavIcon({ path: IconPath.CHECK, color: Color.White, size: 12, weight: 2.6 })
        }
      }
      .width(20)
      .height(20)
      .backgroundColor(this.methodCode === item.code ? Palette.brand : Palette.surface)
      .borderRadius(10)
      .border({
        width: 1,
        color: this.methodCode === item.code ? Palette.brand : '#D8D0C7'
      })

      Column() {
        Text(item.name)
          .fontSize(FontSize.body)
          .fontColor(Palette.ink)
        if (item.desc.length > 0) {
          Text(item.desc)
            .fontSize(FontSize.tiny)
            .fontColor(Palette.ink3)
            .margin({ top: 2 })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .margin({ left: 12 })
    }
    .width('100%')
    .padding({ top: 12, bottom: 12 })
    .onClick(() => {
      this.methodCode = item.code;
    })
  }

  build() {
    NavDestination() {
      Column() {
        Row().height(Layout.statusBar)

        Row() {
          Row() {
            NavIcon({ path: IconPath.BACK, color: Palette.ink, size: 22, weight: 2 })
          }
          .width(40)
          .height(40)
          .onClick(() => Nav.back())

          Text('收银台')
            .fontSize(16)
            .fontWeight(FontWeight.Bold)
            .fontColor(Palette.ink)
            .layoutWeight(1)
            .textAlign(TextAlign.Center)

          Row().width(40).height(40)
        }
        .width('100%')
        .height(Layout.navBar)
        .padding({ left: 8, right: 8 })

        if (this.paid) {
          // ===== 支付成功态 =====
          Column() {
            Stack() {
              Circle()
                .width(76)
                .height(76)
                .fill(Palette.okBg)
              NavIcon({ path: IconPath.CHECK, color: Palette.ok, size: 38, weight: 2.4 })
            }

            Text('支付成功')
              .fontSize(20)
              .fontWeight(FontWeight.Bold)
              .fontColor(Palette.ink)
              .margin({ top: 18 })

            Text('支付单号 ' + this.paymentNo)
              .fontSize(FontSize.tiny)
              .fontColor(Palette.ink3)
              .margin({ top: 8 })

            Text('已支付 ¥' + Format.money(this.param.payableAmount))
              .fontSize(FontSize.body)
              .fontColor(Palette.ink2)
              .margin({ top: 6 })

            Button('查看订单')
              .width(180)
              .height(Layout.btnHeight)
              .fontSize(15)
              .fontWeight(FontWeight.Medium)
              .fontColor(Color.White)
              .backgroundColor(Palette.brand)
              .borderRadius(Radius.btn)
              .margin({ top: 28 })
              .onClick(() => this.goOrder())

            Blank()
          }
          .width('100%')
          .layoutWeight(1)
          .padding({ top: 60 })
          .alignItems(HorizontalAlign.Center)
        } else {
          Scroll() {
            Column() {
              // 金额
              Column() {
                Text('需支付')
                  .fontSize(FontSize.minor)
                  .fontColor(Palette.ink3)
                PriceText({ amount: this.param.payableAmount, large: true })
                  .margin({ top: 6 })
                Row() {
                  NavIcon({ path: IconPath.CLOCK, color: Palette.warn, size: 13, weight: 2 })
                  Text(this.countdown > 0 ?
                    '支付剩余 ' + Format.countdown(this.countdown) : '支付已超时')
                    .fontSize(FontSize.tiny)
                    .fontColor(this.countdown > 0 ? Palette.warn : Palette.danger)
                    .margin({ left: 4 })
                }
                .margin({ top: 10 })
              }
              .width('100%')
              .padding({ top: 26, bottom: 26 })
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .alignItems(HorizontalAlign.Center)

              // 支付方式
              Column() {
                Text('支付方式')
                  .fontSize(FontSize.h2)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(Palette.ink)
                  .width('100%')
                  .margin({ bottom: 4 })

                ForEach(PAY_METHODS, (item: PayMethod) => {
                  this.methodRow(item)
                  if (item.code !== 'balance') {
                    Divider().color(Palette.line)
                  }
                }, (item: PayMethod) => item.code)
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10 })
              .alignItems(HorizontalAlign.Start)

              // 说明
              Column() {
                Text('关于本次支付')
                  .fontSize(FontSize.minor)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(Palette.ink)
                  .width('100%')
                Text('演示环境接入的是模拟支付网关，点击支付会直接返回成功，不会产生真实扣款。' +
                  '真实接入时需要把渠道换成微信/支付宝并走各自的下单与回调流程。')
                  .fontSize(10.5)
                  .fontColor(Palette.ink3)
                  .lineHeight(17)
                  .width('100%')
                  .margin({ top: 6 })
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10 })
              .alignItems(HorizontalAlign.Start)

              Row().height(20)
            }
            .width('100%')
            .padding({ left: Space.page, right: Space.page })
          }
          .layoutWeight(1)
          .scrollBar(BarState.Off)

          Row() {
            Button(this.paying ? '支付中…' : (this.countdown === 0 ? '已超时' : '确认支付'))
              .width('100%')
              .height(Layout.btnHeight)
              .fontSize(15)
              .fontWeight(FontWeight.Medium)
              .fontColor(Color.White)
              .backgroundColor(this.paying || this.countdown === 0 ? '#F5A98C' : Palette.brand)
              .borderRadius(Radius.btn)
              .enabled(!this.paying && this.countdown > 0)
              .onClick(() => this.pay())
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
