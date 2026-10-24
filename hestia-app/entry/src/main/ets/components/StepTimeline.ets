import { FontSize, Palette } from '../common/Theme';
import { TimelineStep } from '../model/Booking';
import { Format } from '../common/Format';

/**
 * 订单进度时间线。
 *
 * 三态：done 已完成（主色实心点）/ now 进行中（主色点 + 浅色光环）/ todo 未开始（灰点）。
 * 数据来自 GET /customer/bookings/{orderNo}/fulfillment，不是本地假动画。
 */
@Component
export struct StepTimeline {
  @Prop steps: TimelineStep[];

  private dotColor(state: string): string {
    return state === 'todo' ? '#E3DCD4' : Palette.brand;
  }

  build() {
    Column() {
      ForEach(this.steps, (step: TimelineStep, index: number) => {
        Row() {
          Column() {
            Stack() {
              if (step.state === 'now') {
                Circle()
                  .width(18)
                  .height(18)
                  .fill(Palette.brandLight)
              }
              Circle()
                .width(step.state === 'now' ? 10 : 8)
                .height(step.state === 'now' ? 10 : 8)
                .fill(this.dotColor(step.state))
            }
            .width(18)
            .height(18)

            if (index < this.steps.length - 1) {
              Divider()
                .vertical(true)
                .height(32)
                .strokeWidth(1.5)
                .color(step.state === 'done' ? '#F8C4AC' : '#EDE6DE')
            }
          }
          .width(22)
          .alignItems(HorizontalAlign.Center)

          Column() {
            Text(step.title)
              .fontSize(FontSize.body)
              .fontWeight(step.state === 'todo' ? FontWeight.Normal : FontWeight.Medium)
              .fontColor(step.state === 'todo' ? Palette.ink3 : Palette.ink)
            if (step.time !== undefined && step.time !== null && step.time.length > 0) {
              Text(Format.dateTime(step.time))
                .fontSize(FontSize.tiny)
                .fontColor(Palette.ink3)
                .margin({ top: 2 })
            }
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
          .padding({ bottom: index < this.steps.length - 1 ? 16 : 0 })
        }
        .width('100%')
        .alignItems(VerticalAlign.Top)
      }, (step: TimelineStep, index: number) => step.title + index.toString() + step.state)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
