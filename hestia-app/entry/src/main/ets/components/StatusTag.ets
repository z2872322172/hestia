import { FontSize, Radius } from '../common/Theme';
import { orderStatusStyle } from '../model/Enums';

/** 订单状态标签：圆角 6、10.5 号字、浅底深字。 */
@Component
export struct StatusTag {
  @Prop status: string = '';

  build() {
    Text(orderStatusStyle(this.status).text)
      .fontSize(FontSize.tag)
      .fontColor(orderStatusStyle(this.status).color)
      .backgroundColor(orderStatusStyle(this.status).bg)
      .borderRadius(Radius.tag)
      .padding({ left: 7, right: 7, top: 3, bottom: 3 })
  }
}
