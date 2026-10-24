import { FontSize, Palette, Space } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from './NavIcon';

/** 区块标题：左标题 + 右侧"全部 >"。 */
@Component
export struct SectionHeader {
  @Prop title: string = '';
  @Prop actionText: string = '';
  onAction: () => void = () => {
  };

  build() {
    Row() {
      Text(this.title)
        .fontSize(FontSize.h2)
        .fontWeight(FontWeight.Bold)
        .fontColor(Palette.ink)
      Blank()
      if (this.actionText.length > 0) {
        Row() {
          Text(this.actionText)
            .fontSize(FontSize.tiny)
            .fontColor(Palette.ink3)
          NavIcon({ path: IconPath.CHEVRON, color: Palette.ink3, size: 12, weight: 2.2 })
            .margin({ left: 2 })
        }
        .onClick(() => this.onAction())
      }
    }
    .width('100%')
    .padding({ left: Space.page, right: Space.page, top: 16, bottom: 10 })
  }
}
