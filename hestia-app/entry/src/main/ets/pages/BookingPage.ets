import util from '@ohos.util';
import common from '@ohos.app.ability.common';
import { FontSize, Layout, Palette, Radius, Space } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import { PriceText } from '../components/PriceText';
import {
  OptionSelection,
  PricePreview,
  ServiceItemDetail,
  ServiceOption,
  ServiceOptionValue
} from '../model/Catalog';
import { Availability, BookingCreated, WindowAvailability } from '../model/Booking';
import { Address } from '../model/Customer';
import { BookingCreateBody } from '../api/BookingApi';
import { BookingApi } from '../api/BookingApi';
import { CatalogApi } from '../api/CatalogApi';
import { CustomerApi } from '../api/CustomerApi';
import { ApiClient } from '../api/ApiClient';
import { MockData } from '../mock/MockData';
import { Nav } from '../common/Nav';
import { AddressParam, BookingParam, PaymentParam, RouteName } from '../common/RouteName';
import { Format } from '../common/Format';
import { Toast } from '../common/Toast';
import { Bar } from '../common/Bar';

/**
 * 09 预约确认。
 *
 * 金额一律走 price-preview 服务端计算，客户端不参与任何金额运算。
 * 幂等键在页面初始化时生成一次，整个页面生命周期不变——这样"点两次提交"只会建一单，
 * 但用户返回修改内容后重进页面会拿到新键，不会误判成重复提交。
 */
@Component
export struct BookingPage {
  param: BookingParam = { itemNo: '', selectedOptions: [] };

  @State detail: ServiceItemDetail | null = null;
  @State address: Address | null = null;
  @State dates: string[] = [];
  @State availabilities: Availability[] = [];
  @State selectedDate: string = '';
  @State selectedWindow: string = '';
  @State quantity: number = 1;
  @State selections: OptionSelection[] = [];
  @State preview: PricePreview | null = null;
  @State loading: boolean = true;
  @State submitting: boolean = false;

  private idemKey: string = util.generateRandomUUID(true);
  private firstShown: boolean = true;

  aboutToAppear(): void {
    Bar.light(getContext(this) as common.Context);
    this.dates = Format.dateRange(14);
    this.selectedDate = this.dates[0];
    this.selections = this.param.selectedOptions;
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    try {
      this.detail = await CatalogApi.detail(this.param.itemNo);
    } catch (err) {
      this.detail = MockData.detail(this.param.itemNo);
    }
    await this.loadAddress();
    await this.loadAvailability();
    await this.refreshPrice();
    this.loading = false;
  }

  private async loadAddress(): Promise<void> {
    try {
      const list: Address[] = await CustomerApi.addresses();
      for (let i = 0; i < list.length; i++) {
        if (list[i].isDefault) {
          this.address = list[i];
        }
      }
      if (this.address === null && list.length > 0) {
        this.address = list[0];
      }
    } catch (err) {
      const list: Address[] = MockData.addresses();
      this.address = list[0];
    }
  }

  /** 一次拉满 14 天，避免每切一天发一次请求。区间超过 14 天后端会拒绝。 */
  private async loadAvailability(): Promise<void> {
    if (this.detail === null) {
      return;
    }
    let areaId: number = 0;
    if (this.address !== null && this.address.serviceAreaId !== undefined &&
      this.address.serviceAreaId !== null) {
      areaId = this.address.serviceAreaId;
    }
    try {
      this.availabilities = await BookingApi.availability(
        this.detail.itemNo, areaId, this.dates[0], this.dates[this.dates.length - 1]);
    } catch (err) {
      this.availabilities = MockData.availabilities();
    }
  }

  private windowsOfSelectedDate(): WindowAvailability[] {
    for (let i = 0; i < this.availabilities.length; i++) {
      if (this.availabilities[i].serviceDate === this.selectedDate) {
        return this.availabilities[i].windows;
      }
    }
    return [];
  }

  private async refreshPrice(): Promise<void> {
    if (this.detail === null) {
      return;
    }
    try {
      this.preview = await CatalogApi.pricePreview(
        this.detail.itemNo, this.quantity, this.selections);
    } catch (err) {
      // 报价失败保留上一次结果，不打断用户
    }
  }

  private isSelected(optionCode: string, valueCode: string): boolean {
    for (let i = 0; i < this.selections.length; i++) {
      if (this.selections[i].optionCode === optionCode && this.selections[i].valueCode === valueCode) {
        return true;
      }
    }
    return false;
  }

  private toggleAddon(optionCode: string, valueCode: string): void {
    const next: OptionSelection[] = [];
    let removed: boolean = false;
    for (let i = 0; i < this.selections.length; i++) {
      const s: OptionSelection = this.selections[i];
      if (s.optionCode === optionCode && s.valueCode === valueCode) {
        removed = true;
      } else {
        next.push(s);
      }
    }
    if (!removed) {
      const added: OptionSelection = { optionCode: optionCode, valueCode: valueCode };
      next.push(added);
    }
    this.selections = next;
    this.refreshPrice();
  }

  private changeQuantity(delta: number): void {
    const next: number = this.quantity + delta;
    if (next < 1) {
      return;
    }
    if (this.detail !== null && next > this.detail.maxQuantity) {
      Toast.show('最多 ' + this.detail.maxQuantity.toString() + ' ' + this.detail.serviceUnit);
      return;
    }
    this.quantity = next;
    this.refreshPrice();
  }

  private payable(): number {
    if (this.preview !== null) {
      return this.preview.payableAmount;
    }
    if (this.detail !== null) {
      return this.detail.basePrice;
    }
    return 0;
  }

  private openAddressPicker(): void {
    const param: AddressParam = { pickMode: true };
    Nav.push(RouteName.ADDRESS, param);
  }

  private async submit(): Promise<void> {
    if (this.submitting) {
      return;
    }
    if (this.address === null) {
      Toast.show('请先添加服务地址');
      return;
    }
    if (this.selectedWindow.length === 0) {
      Toast.show('请选择上门时段');
      return;
    }
    if (this.address.areaMatchStatus === 'OUT_OF_RANGE') {
      Toast.show('该地址暂不在服务范围内，请更换地址');
      return;
    }

    this.submitting = true;
    const body: BookingCreateBody = {
      serviceItemNo: this.param.itemNo,
      appointmentDate: this.selectedDate,
      serviceWindowCode: this.selectedWindow,
      addressNo: this.address.addressNo,
      quantity: this.quantity,
      options: this.selections
    };
    try {
      const created: BookingCreated = await BookingApi.create(body, this.idemKey);
      const payParam: PaymentParam = {
        orderNo: created.orderNo,
        payableAmount: created.payableAmount,
        deadline: created.paymentDeadline
      };
      // 用 replace：下单成功后不允许退回预约页再提交一次
      Nav.replace(RouteName.PAYMENT, payParam);
    } catch (err) {
      if (ApiClient.isBusinessError(err as Object)) {
        Toast.show(ApiClient.messageOf(err as Object));
      } else {
        Toast.show('网络异常，订单未提交，请检查后端是否启动');
      }
      this.submitting = false;
    }
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
        Row().height(Layout.statusBar)

        Row() {
          Row() {
            NavIcon({ path: IconPath.BACK, color: Palette.ink, size: 22, weight: 2 })
          }
          .width(40)
          .height(40)
          .onClick(() => Nav.back())

          Text('确认预约')
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

        if (this.loading) {
          Column() {
            LoadingProgress()
              .width(32)
              .height(32)
              .color(Palette.brand)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          Scroll() {
            Column() {
              // ===== 地址 =====
              Row() {
                NavIcon({ path: IconPath.LOCATION, color: Palette.brand, size: 20, weight: 1.8 })
                Column() {
                  if (this.address !== null) {
                    Row() {
                      Text(this.address.contactName)
                        .fontSize(FontSize.body)
                        .fontWeight(FontWeight.Medium)
                        .fontColor(Palette.ink)
                      Text(this.address.contactPhone)
                        .fontSize(FontSize.minor)
                        .fontColor(Palette.ink2)
                        .margin({ left: 8 })
                      if (this.address.isDefault) {
                        Text('默认')
                          .fontSize(9.5)
                          .fontColor(Palette.brandDeep)
                          .backgroundColor(Palette.brandXLight)
                          .borderRadius(4)
                          .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                          .margin({ left: 6 })
                      }
                    }
                    Text(this.address.detailAddress)
                      .fontSize(FontSize.minor)
                      .fontColor(Palette.ink2)
                      .maxLines(2)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                      .margin({ top: 5 })
                    if (this.address.areaMatchStatus === 'OUT_OF_RANGE') {
                      Text('暂不在服务范围内，请更换地址')
                        .fontSize(FontSize.tag)
                        .fontColor(Palette.danger)
                        .margin({ top: 4 })
                    }
                  } else {
                    Text('请添加服务地址')
                      .fontSize(FontSize.body)
                      .fontColor(Palette.ink2)
                  }
                }
                .alignItems(HorizontalAlign.Start)
                .layoutWeight(1)
                .margin({ left: 10 })

                NavIcon({ path: IconPath.CHEVRON, color: '#C9C2BA', size: 14, weight: 2 })
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .alignItems(VerticalAlign.Top)
              .onClick(() => this.openAddressPicker())

              // ===== 日期 =====
              Column() {
                Text('服务日期')
                  .fontSize(FontSize.h2)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(Palette.ink)
                  .width('100%')
                  .margin({ bottom: 10 })

                Scroll() {
                  Row() {
                    ForEach(this.dates, (date: string) => {
                      Column() {
                        Text(Format.weekday(date))
                          .fontSize(10.5)
                          .fontColor(this.selectedDate === date ? Color.White : Palette.ink2)
                        Text(Format.dayOfMonth(date))
                          .fontSize(15)
                          .fontWeight(FontWeight.Bold)
                          .fontColor(this.selectedDate === date ? Color.White : Palette.ink)
                          .margin({ top: 2 })
                      }
                      .width(46)
                      .height(58)
                      .backgroundColor(this.selectedDate === date ? Palette.brand : Palette.surface)
                      .borderRadius(12)
                      .border({
                        width: 1,
                        color: this.selectedDate === date ? Palette.brand : Palette.line
                      })
                      .justifyContent(FlexAlign.Center)
                      .margin({ right: 8 })
                      .onClick(() => {
                        this.selectedDate = date;
                        this.selectedWindow = '';
                      })
                    }, (date: string) => date)
                  }
                }
                .width('100%')
                .scrollable(ScrollDirection.Horizontal)
                .scrollBar(BarState.Off)

                Text('服务时段')
                  .fontSize(FontSize.h2)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(Palette.ink)
                  .width('100%')
                  .margin({ top: 16, bottom: 10 })

                if (this.windowsOfSelectedDate().length === 0) {
                  Text('该日期暂无可约时段，请选择其他日期')
                    .fontSize(FontSize.minor)
                    .fontColor(Palette.ink3)
                    .width('100%')
                    .padding({ top: 10, bottom: 10 })
                } else {
                  Flex({ wrap: FlexWrap.Wrap }) {
                    ForEach(this.windowsOfSelectedDate(), (w: WindowAvailability) => {
                      Column() {
                        Text(w.windowName)
                          .fontSize(FontSize.minor)
                          .fontColor(!w.available ? '#C6BEB6' :
                          (this.selectedWindow === w.windowCode ? Palette.brandDeep : Palette.ink))
                          .fontWeight(this.selectedWindow === w.windowCode ?
                          FontWeight.Medium : FontWeight.Normal)
                          .decoration({
                            type: w.available ? TextDecorationType.None : TextDecorationType.LineThrough
                          })
                      }
                      .width('48%')
                      .height(40)
                      .backgroundColor(!w.available ? '#F7F4F0' :
                      (this.selectedWindow === w.windowCode ? Palette.brandXLight : Palette.surface))
                      .borderRadius(10)
                      .border({
                        width: 1,
                        color: this.selectedWindow === w.windowCode ? Palette.brand : Palette.line
                      })
                      .justifyContent(FlexAlign.Center)
                      .margin({ bottom: 8 })
                      .onClick(() => {
                        if (!w.available) {
                          Toast.show('该时段已约满');
                          return;
                        }
                        this.selectedWindow = w.windowCode;
                      })
                    }, (w: WindowAvailability) => w.windowCode)
                  }
                  .width('100%')
                  .justifyContent(FlexAlign.SpaceBetween)
                }
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10 })
              .alignItems(HorizontalAlign.Start)

              // ===== 数量 =====
              Row() {
                Column() {
                  Text('服务数量')
                    .fontSize(FontSize.body)
                    .fontColor(Palette.ink)
                  Text('单位：' + (this.detail !== null ? this.detail.serviceUnit : '次'))
                    .fontSize(FontSize.tiny)
                    .fontColor(Palette.ink3)
                    .margin({ top: 3 })
                }
                .alignItems(HorizontalAlign.Start)

                Blank()

                Row() {
                  Text('−')
                    .fontSize(18)
                    .fontColor(this.quantity <= 1 ? '#C6BEB6' : Palette.ink)
                    .width(32)
                    .height(32)
                    .textAlign(TextAlign.Center)
                    .backgroundColor(Palette.grayBg)
                    .borderRadius(8)
                    .onClick(() => this.changeQuantity(-1))
                  Text(this.quantity.toString())
                    .fontSize(15)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(Palette.ink)
                    .width(44)
                    .textAlign(TextAlign.Center)
                    .fontFeature('"tnum" on')
                  Text('+')
                    .fontSize(18)
                    .fontColor(Palette.ink)
                    .width(32)
                    .height(32)
                    .textAlign(TextAlign.Center)
                    .backgroundColor(Palette.grayBg)
                    .borderRadius(8)
                    .onClick(() => this.changeQuantity(1))
                }
                .alignItems(VerticalAlign.Center)
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10 })

              // ===== 加项 =====
              if (this.detail !== null && this.detail.options.length > 0) {
                Column() {
                  Text('可选加项')
                    .fontSize(FontSize.h2)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(Palette.ink)
                    .width('100%')
                    .margin({ bottom: 10 })

                  ForEach(this.detail.options, (opt: ServiceOption) => {
                    Column() {
                      ForEach(opt.values, (value: ServiceOptionValue) => {
                        Row() {
                          Stack() {
                            if (this.isSelected(opt.optionCode, value.valueCode)) {
                              NavIcon({
                                path: IconPath.CHECK,
                                color: Color.White,
                                size: 12,
                                weight: 2.6
                              })
                            }
                          }
                          .width(18)
                          .height(18)
                          .backgroundColor(this.isSelected(opt.optionCode, value.valueCode) ?
                          Palette.brand : Palette.surface)
                          .borderRadius(4)
                          .border({
                            width: 1,
                            color: this.isSelected(opt.optionCode, value.valueCode) ?
                            Palette.brand : '#D8D0C7'
                          })

                          Text(value.valueName)
                            .fontSize(FontSize.minor)
                            .fontColor(Palette.ink)
                            .margin({ left: 10 })
                            .layoutWeight(1)

                          Text('+¥' + Format.money(value.extraPrice))
                            .fontSize(FontSize.minor)
                            .fontColor(Palette.brand)
                            .fontFeature('"tnum" on')
                        }
                        .width('100%')
                        .padding({ top: 8, bottom: 8 })
                        .onClick(() => this.toggleAddon(opt.optionCode, value.valueCode))
                      }, (value: ServiceOptionValue) => opt.optionCode + value.valueCode)
                    }
                    .width('100%')
                  }, (opt: ServiceOption) => opt.optionCode)
                }
                .width('100%')
                .padding(Space.cardPad)
                .backgroundColor(Palette.surface)
                .borderRadius(Radius.card)
                .margin({ top: 10 })
                .alignItems(HorizontalAlign.Start)
              }

              // ===== 费用明细 =====
              Column() {
                Text('费用明细')
                  .fontSize(FontSize.h2)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(Palette.ink)
                  .width('100%')
                  .margin({ bottom: 4 })

                if (this.preview !== null) {
                  this.costRow('服务费', this.preview.baseAmount, false)
                  if (this.preview.addonAmount > 0) {
                    this.costRow('加项费用', this.preview.addonAmount, false)
                  }
                  if (this.preview.materialAmount > 0) {
                    this.costRow('材料费', this.preview.materialAmount, false)
                  }
                  if (this.preview.travelFee > 0) {
                    this.costRow('上门费', this.preview.travelFee, false)
                  }
                  if (this.preview.discountAmount > 0) {
                    this.costRow('优惠', -this.preview.discountAmount, false)
                  }
                  Divider()
                    .color(Palette.line)
                    .margin({ top: 4, bottom: 4 })
                  this.costRow('应付金额', this.preview.payableAmount, true)
                  if (this.preview.pricingExplanation.length > 0) {
                    Column() {
                      ForEach(this.preview.pricingExplanation, (line: string) => {
                        Text('· ' + line)
                          .fontSize(10)
                          .fontColor(Palette.ink3)
                          .width('100%')
                          .margin({ top: 2 })
                      }, (line: string, index: number) => 'exp' + index.toString())
                    }
                    .width('100%')
                    .margin({ top: 6 })
                    .alignItems(HorizontalAlign.Start)
                  }
                } else {
                  Text('正在计算价格…')
                    .fontSize(FontSize.minor)
                    .fontColor(Palette.ink3)
                    .margin({ top: 8 })
                }

                Text('金额由服务端按服务项快照计算，客户端无法修改')
                  .fontSize(10)
                  .fontColor(Palette.ink3)
                  .margin({ top: 10 })
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10 })
              .alignItems(HorizontalAlign.Start)

              Row().height(24)
            }
            .width('100%')
            .padding({ left: Space.page, right: Space.page })
          }
          .layoutWeight(1)
          .scrollBar(BarState.Off)

          // ===== 底部提交 =====
          Row() {
            Column() {
              PriceText({ amount: this.payable(), large: true })
              Text('提交后 15 分钟内完成支付')
                .fontSize(9.5)
                .fontColor(Palette.ink3)
                .margin({ top: 2 })
            }
            .alignItems(HorizontalAlign.Start)

            Blank()

            Button(this.submitting ? '提交中…' : '提交订单')
              .width(140)
              .height(Layout.btnHeight)
              .fontSize(15)
              .fontWeight(FontWeight.Medium)
              .fontColor(Color.White)
              .backgroundColor(this.submitting ? '#F5A98C' : Palette.brand)
              .borderRadius(Radius.btn)
              .enabled(!this.submitting)
              .onClick(() => this.submit())
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
    .onShown(() => {
      // 首次显示由 aboutToAppear 负责，从地址页选完返回时再刷新一次
      if (this.firstShown) {
        this.firstShown = false;
        return;
      }
      this.loadAddress().then(() => {
        this.loadAvailability();
        this.refreshPrice();
      });
    })
  }
}
