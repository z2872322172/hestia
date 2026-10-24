import { FontSize, Palette } from '../common/Theme';
import { Format } from '../common/Format';

/** 价格：¥ 号小、数字大，数字等宽避免刷新时跳动。 */
@Component
export struct PriceText {
  @Prop amount: number = 0;
  @Prop large: boolean = false;
  @Prop suffix: string = '';
  @Prop color: string = Palette.brand;

  build() {
    Row() {
      Text('¥')
        .fontSize(this.large ? 14 : 12)
        .fontColor(this.color)
        .fontWeight(FontWeight.Bold)
        .margin({ right: 1 })
      Text(Format.money(this.amount))
        .fontSize(this.large ? FontSize.priceLg : FontSize.priceSm)
        .fontColor(this.color)
        .fontWeight(FontWeight.Bold)
        .fontFeature('"tnum" on')
      if (this.suffix.length > 0) {
        Text(this.suffix)
          .fontSize(FontSize.tiny)
          .fontColor(Palette.ink3)
          .margin({ left: 2 })
      }
    }
    .alignItems(VerticalAlign.Bottom)
  }
}
