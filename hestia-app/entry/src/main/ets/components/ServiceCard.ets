import { FontSize, Palette, Radius, Space, categoryBg, categoryIconColor } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { ServiceItemSummary } from '../model/Catalog';
import { NavIcon } from './NavIcon';
import { PriceText } from './PriceText';

/** 服务卡片：左 64×64 渐变缩略块 + 右侧标题/描述/标签/价格。 */
@Component
export struct ServiceCard {
  @Prop item: ServiceItemSummary;
  @Prop index: number = 0;
  onTap: () => void = () => {
  };

  private durationText(): string {
    const hours: number = Math.round(this.item.durationMinutes / 6) / 10;
    return hours.toString() + ' 小时';
  }

  build() {
    Row() {
      Stack() {
        NavIcon({
          path: IconPath.HOUSE_FILLED,
          color: categoryIconColor(this.index),
          size: 30,
          weight: 1.4
        })
      }
      .width(64)
      .height(64)
      .backgroundColor(categoryBg(this.index))
      .borderRadius(Radius.iconBox)

      Column() {
        Text(this.item.itemName)
          .fontSize(FontSize.cardTitle)
          .fontWeight(FontWeight.Medium)
          .fontColor(Palette.ink)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        Text(this.item.shortDescription)
          .fontSize(FontSize.minor)
          .fontColor(Palette.ink2)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .margin({ top: 3 })

        Row() {
          Text(this.item.categoryName)
            .fontSize(FontSize.tag)
            .fontColor(Palette.brandDeep)
            .backgroundColor(Palette.brandXLight)
            .borderRadius(Radius.tag)
            .padding({ left: 7, right: 7, top: 2, bottom: 2 })
          Text(this.durationText())
            .fontSize(FontSize.tag)
            .fontColor(Palette.ink2)
            .backgroundColor(Palette.grayBg)
            .borderRadius(Radius.tag)
            .padding({ left: 7, right: 7, top: 2, bottom: 2 })
            .margin({ left: 6 })
        }
        .margin({ top: 7 })

        Row() {
          PriceText({
            amount: this.item.basePrice,
            suffix: '/' + this.item.serviceUnit
          })
          Blank()
          Text(this.item.pricingMode === 'TIERED' ? '按量计价' : '一口价')
            .fontSize(FontSize.tiny)
            .fontColor(Palette.ink3)
        }
        .width('100%')
        .margin({ top: 9 })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 12 })
    }
    .width('100%')
    .padding(Space.cardPad)
    .backgroundColor(Palette.surface)
    .borderRadius(Radius.card)
    .alignItems(VerticalAlign.Top)
    .onClick(() => this.onTap())
  }
}
