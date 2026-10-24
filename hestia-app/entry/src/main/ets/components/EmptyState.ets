import { FontSize, Palette, Radius } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from './NavIcon';

/** 空状态：一个线框图标 + 一句引导，避免"没数据"时页面空白尴尬。 */
@Component
export struct EmptyState {
  @Prop title: string = '暂无数据';
  @Prop desc: string = '';
  @Prop actionText: string = '';
  @Prop iconPath: string = IconPath.ORDER;
  onAction: () => void = () => {
  };

  build() {
    Column() {
      NavIcon({ path: this.iconPath, color: '#D8D0C7', size: 56, weight: 1.1 })
      Text(this.title)
        .fontSize(FontSize.cardTitle)
        .fontColor(Palette.ink2)
        .margin({ top: 14 })
      if (this.desc.length > 0) {
        Text(this.desc)
          .fontSize(FontSize.tiny)
          .fontColor(Palette.ink3)
          .margin({ top: 6 })
          .textAlign(TextAlign.Center)
      }
      if (this.actionText.length > 0) {
        Button(this.actionText)
          .fontSize(FontSize.body)
          .fontColor(Palette.brand)
          .backgroundColor(Palette.brandXLight)
          .height(36)
          .borderRadius(Radius.btn)
          .padding({ left: 20, right: 20 })
          .margin({ top: 18 })
          .onClick(() => this.onAction())
      }
    }
    .width('100%')
    .padding({ top: 70, bottom: 70, left: 32, right: 32 })
    .alignItems(HorizontalAlign.Center)
  }
}
