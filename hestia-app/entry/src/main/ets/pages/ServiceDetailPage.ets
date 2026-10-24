import { FontSize, Layout, Palette, Radius, Space, categoryBg } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import { PriceText } from '../components/PriceText';
import { OptionSelection, ServiceItemDetail, ServiceOption, ServiceOptionValue, StandardGroup, StandardItem } from '../model/Catalog';
import { CatalogApi } from '../api/CatalogApi';
import { ApiClient } from '../api/ApiClient';
import { MockData } from '../mock/MockData';
import { Nav } from '../common/Nav';
import { BookingParam, RouteName, ServiceDetailParam } from '../common/RouteName';
import { Bar } from '../common/Bar';
import { Toast } from '../common/Toast';
import { Format } from '../common/Format';
import common from '@ohos.app.ability.common';

/** 08 服务详情。 */
@Component
export struct ServiceDetailPage {
  param: ServiceDetailParam = { itemNo: '' };

  @State detail: ServiceItemDetail | null = null;
  @State selections: OptionSelection[] = [];
  @State loading: boolean = true;
  @State errorMsg: string = '';
  private dots: number[] = [0, 1, 2];
  private stars: number[] = [0, 1, 2, 3, 4];

  aboutToAppear(): void {
    Bar.light(getContext(this) as common.Context);
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    try {
      this.detail = await CatalogApi.detail(this.param.itemNo);
    } catch (err) {
      if (ApiClient.isBusinessError(err as Object)) {
        this.errorMsg = ApiClient.messageOf(err as Object);
      } else {
        this.detail = MockData.detail(this.param.itemNo);
      }
    }
    this.loading = false;
  }

  private isSelected(optionCode: string, valueCode: string): boolean {
    for (let i = 0; i < this.selections.length; i++) {
      if (this.selections[i].optionCode === optionCode && this.selections[i].valueCode === valueCode) {
        return true;
      }
    }
    return false;
  }

  /** 单选替换同组，多选 toggle。每次都重建数组，保证 @State 能感知变化。 */
  private toggle(optionCode: string, valueCode: string, multi: boolean): void {
    const next: OptionSelection[] = [];
    let removed: boolean = false;
    if (multi) {
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
    } else {
      for (let i = 0; i < this.selections.length; i++) {
        if (this.selections[i].optionCode !== optionCode) {
          next.push(this.selections[i]);
        }
      }
      const added: OptionSelection = { optionCode: optionCode, valueCode: valueCode };
      next.push(added);
    }
    this.selections = next;
  }

  private groupStandards(): StandardGroup[] {
    const groups: StandardGroup[] = [];
    if (this.detail === null) {
      return groups;
    }
    for (let i = 0; i < this.detail.standards.length; i++) {
      const item: StandardItem = this.detail.standards[i];
      let found: StandardGroup | null = null;
      for (let j = 0; j < groups.length; j++) {
        if (groups[j].groupName === item.standardGroup) {
          found = groups[j];
        }
      }
      if (found === null) {
        const created: StandardGroup = { groupName: item.standardGroup, items: [item] };
        groups.push(created);
      } else {
        found.items.push(item);
      }
    }
    return groups;
  }

  /** 校验必选规格，缺了就提示，不让他跳到预约页再被后端打回。 */
  private goBooking(): void {
    if (this.detail === null) {
      return;
    }
    for (let i = 0; i < this.detail.options.length; i++) {
      const opt: ServiceOption = this.detail.options[i];
      if (!opt.required) {
        continue;
      }
      let picked: boolean = false;
      for (let j = 0; j < this.selections.length; j++) {
        if (this.selections[j].optionCode === opt.optionCode) {
          picked = true;
        }
      }
      if (!picked) {
        Toast.show('请选择' + opt.optionName);
        return;
      }
    }
    const param: BookingParam = {
      itemNo: this.detail.itemNo,
      selectedOptions: this.selections
    };
    Nav.push(RouteName.BOOKING, param);
  }

  @Builder
  optionChip(opt: ServiceOption, value: ServiceOptionValue) {
    Column() {
      Text(value.valueName)
        .fontSize(FontSize.minor)
        .fontColor(this.isSelected(opt.optionCode, value.valueCode) ? Palette.brandDeep : Palette.ink)
        .fontWeight(this.isSelected(opt.optionCode, value.valueCode) ?
        FontWeight.Medium : FontWeight.Normal)
      if (value.extraPrice > 0) {
        Text('+¥' + Format.money(value.extraPrice))
          .fontSize(FontSize.tag)
          .fontColor(Palette.ink3)
          .margin({ top: 2 })
      }
    }
    .padding({ left: 12, right: 12, top: 8, bottom: 8 })
    .backgroundColor(this.isSelected(opt.optionCode, value.valueCode) ?
    Palette.brandXLight : Palette.surface)
    .borderRadius(10)
    .border({
      width: 1,
      color: this.isSelected(opt.optionCode, value.valueCode) ? Palette.brand : Palette.line
    })
    .margin({ right: 8, bottom: 8 })
    .onClick(() => {
      this.toggle(opt.optionCode, value.valueCode, opt.selectionType === 'MULTI');
    })
  }

  build() {
    NavDestination() {
      Column() {
        if (this.loading) {
          Column() {
            LoadingProgress()
              .width(36)
              .height(36)
              .color(Palette.brand)
          }
          .width('100%')
          .height('100%')
          .justifyContent(FlexAlign.Center)
        } else if (this.detail === null) {
          Column() {
            Text(this.errorMsg.length > 0 ? this.errorMsg : '服务不存在')
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
              // ===== Hero =====
              Stack({ alignContent: Alignment.TopStart }) {
                Column()
                  .width('100%')
                  .height(250)
                  .linearGradient({
                    angle: 160,
                    colors: [['#FFD9C4', 0.0], ['#FFC1A3', 0.55], ['#F7A981', 1.0]]
                  })

                // 装饰
                Circle()
                  .width(150)
                  .height(150)
                  .fill('rgba(255,255,255,0.28)')
                  .position({ x: 230, y: 20 })
                Circle()
                  .width(90)
                  .height(90)
                  .fill('rgba(255,255,255,0.22)')
                  .position({ x: 40, y: 160 })

                // 居中图标
                Stack() {
                  NavIcon({ path: IconPath.HOUSE_FILLED, color: '#C2592C', size: 64, weight: 1.3 })
                }
                .width('100%')
                .height(250)
                .justifyContent(FlexAlign.Center)
                .alignItems(HorizontalAlign.Center)

                // 返回
                Row() {
                  NavIcon({ path: IconPath.BACK, color: Palette.ink, size: 20, weight: 2 })
                }
                .width(32)
                .height(32)
                .backgroundColor('rgba(255,255,255,0.82)')
                .borderRadius(16)
                .justifyContent(FlexAlign.Center)
                .position({ x: 14, y: Layout.statusBar + 12 })
                .onClick(() => Nav.back())

                // 指示点
                Row() {
                  ForEach(this.dots, (i: number) => {
                    Row()
                      .width(i === 0 ? 14 : 5)
                      .height(5)
                      .borderRadius(3)
                      .backgroundColor(i === 0 ? Color.White : 'rgba(255,255,255,0.55)')
                      .margin({ right: 4 })
                  }, (i: number) => 'dot' + i.toString())
                }
                .width('100%')
                .justifyContent(FlexAlign.Center)
                .position({ x: 0, y: 230 })
              }
              .width('100%')
              .height(250)

              // ===== 主信息 =====
              Column() {
                Text(this.detail.itemName)
                  .fontSize(19)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(Palette.ink)
                  .width('100%')

                Text(this.detail.shortDescription)
                  .fontSize(FontSize.minor)
                  .fontColor(Palette.ink2)
                  .width('100%')
                  .margin({ top: 6 })

                Row() {
                  PriceText({
                    amount: this.detail.basePrice,
                    large: true,
                    suffix: this.detail.pricingMode === 'TIERED' ? ' 起' : '/' + this.detail.serviceUnit
                  })
                  Blank()
                  Text(this.detail.pricingMode === 'TIERED' ? '按面积阶梯计价' : '一口价 · 无隐形收费')
                    .fontSize(FontSize.tiny)
                    .fontColor(Palette.ink3)
                }
                .width('100%')
                .margin({ top: 12 })

                Row() {
                  Text(this.detail.categoryName)
                    .fontSize(FontSize.tag)
                    .fontColor(Palette.brandDeep)
                    .backgroundColor(Palette.brandXLight)
                    .borderRadius(Radius.tag)
                    .padding({ left: 7, right: 7, top: 3, bottom: 3 })
                  Text('约 ' + Math.round(this.detail.durationMinutes / 6) / 10 + ' 小时')
                    .fontSize(FontSize.tag)
                    .fontColor(Palette.ink2)
                    .backgroundColor(Palette.grayBg)
                    .borderRadius(Radius.tag)
                    .padding({ left: 7, right: 7, top: 3, bottom: 3 })
                    .margin({ left: 6 })
                }
                .width('100%')
                .margin({ top: 10 })

                if (this.detail.detailDescription.length > 0) {
                  Text(this.detail.detailDescription)
                    .fontSize(FontSize.minor)
                    .fontColor(Palette.ink2)
                    .lineHeight(20)
                    .width('100%')
                    .margin({ top: 12 })
                }
              }
              .width('100%')
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius({ topLeft: Radius.card, topRight: Radius.card })
              .margin({ top: -16 })
              .alignItems(HorizontalAlign.Start)

              // ===== 规格选择 =====
              if (this.detail.options.length > 0) {
                Column() {
                  Text('服务规格')
                    .fontSize(FontSize.h2)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(Palette.ink)
                    .width('100%')
                    .margin({ bottom: 10 })

                  ForEach(this.detail.options, (opt: ServiceOption) => {
                    Column() {
                      Row() {
                        Text(opt.optionName)
                          .fontSize(FontSize.body)
                          .fontColor(Palette.ink)
                          .fontWeight(FontWeight.Medium)
                        if (opt.required) {
                          Text('必选')
                            .fontSize(9.5)
                            .fontColor(Palette.danger)
                            .margin({ left: 6 })
                        }
                        Blank()
                        Text(opt.selectionType === 'MULTI' ? '可多选' : '单选')
                          .fontSize(FontSize.tiny)
                          .fontColor(Palette.ink3)
                      }
                      .width('100%')
                      .margin({ bottom: 10 })

                      Flex({ wrap: FlexWrap.Wrap }) {
                        ForEach(opt.values, (value: ServiceOptionValue) => {
                          this.optionChip(opt, value)
                        }, (value: ServiceOptionValue) => opt.optionCode + value.valueCode)
                      }
                      .width('100%')
                    }
                    .width('100%')
                    .margin({ bottom: 6 })
                    .alignItems(HorizontalAlign.Start)
                  }, (opt: ServiceOption) => opt.optionCode)
                }
                .padding(Space.cardPad)
                .backgroundColor(Palette.surface)
                .borderRadius(Radius.card)
                .margin({ top: 10, left: Space.page, right: Space.page })
                .width('calc(100% - 32vp)')
                .alignItems(HorizontalAlign.Start)
              }

              // ===== 服务标准 =====
              Column() {
                Text('服务标准')
                  .fontSize(FontSize.h2)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(Palette.ink)
                  .width('100%')
                Text('共 ' + this.detail.standards.length.toString() + ' 项，服务完成后逐项验收')
                  .fontSize(FontSize.tiny)
                  .fontColor(Palette.ink3)
                  .width('100%')
                  .margin({ top: 4, bottom: 12 })

                ForEach(this.groupStandards(), (group: StandardGroup) => {
                  Column() {
                    Text(group.groupName)
                      .fontSize(FontSize.minor)
                      .fontWeight(FontWeight.Medium)
                      .fontColor(Palette.brandDeep)
                      .width('100%')
                      .margin({ bottom: 8 })

                    ForEach(group.items, (item: StandardItem) => {
                      Row() {
                        NavIcon({ path: IconPath.CHECK, color: Palette.ok, size: 14, weight: 2.4 })
                          .margin({ top: 1 })
                        Column() {
                          Text(item.itemName)
                            .fontSize(FontSize.minor)
                            .fontColor(Palette.ink)
                          if (item.description.length > 0) {
                            Text(item.description)
                              .fontSize(FontSize.tiny)
                              .fontColor(Palette.ink3)
                              .margin({ top: 2 })
                          }
                        }
                        .alignItems(HorizontalAlign.Start)
                        .layoutWeight(1)
                        .margin({ left: 8 })

                        if (item.evidenceRequired) {
                          Text('需拍照')
                            .fontSize(9.5)
                            .fontColor(Palette.warnText)
                            .backgroundColor(Palette.warnBg)
                            .borderRadius(4)
                            .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                        }
                      }
                      .width('100%')
                      .alignItems(VerticalAlign.Top)
                      .margin({ bottom: 9 })
                    }, (item: StandardItem) => item.standardGroup + item.itemName)
                  }
                  .width('100%')
                  .margin({ bottom: 6 })
                  .alignItems(HorizontalAlign.Start)
                }, (group: StandardGroup) => group.groupName)
              }
              .padding(Space.cardPad)
              .backgroundColor(Palette.surface)
              .borderRadius(Radius.card)
              .margin({ top: 10, left: Space.page, right: Space.page })
              .width('calc(100% - 32vp)')
              .alignItems(HorizontalAlign.Start)

              // ===== 用户评价（静态示例，后端暂无公开评价接口）=====
              Column() {
                Text('用户评价')
                  .fontSize(FontSize.h2)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(Palette.ink)
                  .width('100%')
                Row() {
                  Text('4.9')
                    .fontSize(26)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(Palette.brand)
                    .fontFeature('"tnum" on')
                  Column() {
                    Row() {
                      ForEach(this.stars, (i: number) => {
                        NavIcon({ path: IconPath.STAR, color: Palette.brand, size: 12, filled: true })
                          .margin({ right: 2 })
                      }, (i: number) => 'dstar' + i.toString())
                    }
                    Text('326 条真实评价')
                      .fontSize(FontSize.tiny)
                      .fontColor(Palette.ink3)
                      .margin({ top: 3 })
                  }
                  .alignItems(HorizontalAlign.Start)
                  .margin({ left: 12 })
                  Blank()
                }
                .width('100%')
                .margin({ top: 8 })

                Text('阿姨很细致，厨房油烟机都拆开洗了，走之前还把垃圾带走。')
                  .fontSize(FontSize.minor)
                  .fontColor('#3A3532')
                  .width('100%')
                  .margin({ top: 12 })
              }
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

          // ===== 底部操作栏 =====
          Row() {
            Column() {
              PriceText({
                amount: this.detail.basePrice,
                suffix: this.detail.pricingMode === 'TIERED' ? ' 起' : '/' + this.detail.serviceUnit
              })
              Text('服务后确认完成再评价')
                .fontSize(9.5)
                .fontColor(Palette.ink3)
                .margin({ top: 2 })
            }
            .alignItems(HorizontalAlign.Start)

            Blank()

            Button('立即预约')
              .width(140)
              .height(Layout.btnHeight)
              .fontSize(15)
              .fontWeight(FontWeight.Medium)
              .fontColor(Color.White)
              .backgroundColor(Palette.brand)
              .borderRadius(Radius.btn)
              .onClick(() => this.goBooking())
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
