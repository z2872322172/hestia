import { Palette } from '../common/Theme';

/**
 * 线性图标。
 *
 * 用 Shape + viewPort 把 24×24 的路径缩放到目标尺寸；
 * 如果直接给 Path 设 width/height，坐标不会缩放，图形会被裁切。
 */
@Component
export struct NavIcon {
  @Prop path: string = '';
  @Prop color: string = Palette.ink;
  @Prop size: number = 22;
  @Prop weight: number = 1.8;
  @Prop filled: boolean = false;

  build() {
    Shape() {
      Path()
        .commands(this.path)
        .fill(this.filled ? this.color : Color.Transparent)
        .stroke(this.filled ? Color.Transparent : this.color)
        .strokeWidth(this.weight)
        .strokeLineCap(LineCapStyle.Round)
        .strokeLineJoin(LineJoinStyle.Round)
    }
    .viewPort({ x: 0, y: 0, width: 24, height: 24 })
    .width(this.size)
    .height(this.size)
  }
}
