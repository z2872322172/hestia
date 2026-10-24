import common from '@ohos.app.ability.common';
import { FontSize, Layout, Palette, Radius, Space } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import {
  averageScore,
  DimensionMeta,
  DimensionScores,
  newDimensionScores,
  REVIEW_DIMENSIONS,
  scoreOf,
  SubmitReviewBody,
  withScore
} from '../model/Review';
import { ReviewApi } from '../api/ReviewApi';
import { ApiClient } from '../api/ApiClient';
import { Nav } from '../common/Nav';
import { ReviewParam, RouteName } from '../common/RouteName';
import { Toast } from '../common/Toast';
import { Bar } from '../common/Bar';

/** 12 评价。维度来自 REVIEW_DIMENSIONS（后端暂无维度查询接口，见该常量注释）。 */
@Component
export struct ReviewPage {
  param: ReviewParam = { orderNo: '', serviceName: '' };

  @State scores: DimensionScores = newDimensionScores();
  @State content: string = '';
  @State anonymous: boolean = false;
  @State submitting: boolean = false;

  private starIndexes: number[] = [0, 1, 2, 3, 4];

  aboutToAppear(): void {
    Bar.light(getContext(this) as common.Context);
  }

  private async submit(): Promise<void> {
    if (this.submitting) {
      return;
    }
    this.submitting = true;
    const emptyFiles: number[] = [];
    const body: SubmitReviewBody = {
      dimensionScores: this.scores,
      content: this.content,
      fileIds: emptyFiles
    };
    try {
      await ReviewApi.submit(this.param.orderNo, body);
      Toast.show('评价成功，感谢反馈');
      Nav.back();
    } catch (err) {
      this.submitting = false;
      if (ApiClient.isBusinessError(err as Object)) {
        Toast.show(ApiClient.messageOf(err as Object));
      } else {
        Toast.show('网络异常，评价未提交');
      }
    }
  }

  @Builder
  starRow(code: string, size: number) {
    Row() {
      ForEach(this.starIndexes, (i: number) => {
        NavIcon({
          path: IconPath.STAR,
          color: i < scoreOf(this.scores, code) ? Palette.brand : '#E3DCD4',
          size: size,
          filled: i < scoreOf(this.scores, code)
        })
          .margin({ right: 5 })
          .onClick(() => {
            this.scores = withScore(this.scores, code, i + 1);
          })
      }, (i: number) => code + i.toString())
    }
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

          Text('评价服务')
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

        Scroll() {
          Column() {
            // 服务信息
            Column() {
              Text(this.param.serviceName)
                .fontSize(FontSize.cardTitle)
                .fontWeight(FontWeight.Medium)
                .fontColor(Palette.ink)
                .width('100%')
              Text('订单号 ' + this.param.orderNo)
                .fontSize(FontSize.tiny)
                .fontColor(Palette.ink3)
                .width('100%')
                .margin({ top: 5 })
            }
            .width('100%')
            .padding(Space.cardPad)
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.card)
            .alignItems(HorizontalAlign.Start)

            // 总分
            Column() {
              Text('总体评分')
                .fontSize(FontSize.minor)
                .fontColor(Palette.ink2)
              Text(averageScore(this.scores).toFixed(1))
                .fontSize(34)
                .fontWeight(FontWeight.Bold)
                .fontColor(Palette.brand)
                .fontFeature('"tnum" on')
                .margin({ top: 4 })
              Text(averageScore(this.scores) >= 4.5 ? '非常满意' :
                averageScore(this.scores) >= 3.5 ? '比较满意' :
                  averageScore(this.scores) >= 2.5 ? '一般' : '不满意')
                .fontSize(FontSize.tiny)
                .fontColor(Palette.ink3)
                .margin({ top: 2 })
            }
            .width('100%')
            .padding({ top: 20, bottom: 20 })
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.card)
            .margin({ top: 10 })
            .alignItems(HorizontalAlign.Center)

            // 维度评分
            Column() {
              Text('分项评分')
                .fontSize(FontSize.h2)
                .fontWeight(FontWeight.Bold)
                .fontColor(Palette.ink)
                .width('100%')
                .margin({ bottom: 12 })

              ForEach(REVIEW_DIMENSIONS, (dim: DimensionMeta) => {
                Row() {
                  Text(dim.name)
                    .fontSize(FontSize.body)
                    .fontColor(Palette.ink)
                    .layoutWeight(1)
                  this.starRow(dim.code, 20)
                }
                .width('100%')
                .padding({ top: 6, bottom: 6 })
              }, (dim: DimensionMeta) => dim.code)
            }
            .width('100%')
            .padding(Space.cardPad)
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.card)
            .margin({ top: 10 })
            .alignItems(HorizontalAlign.Start)

            // 文字评价
            Column() {
              Text('评价内容')
                .fontSize(FontSize.h2)
                .fontWeight(FontWeight.Bold)
                .fontColor(Palette.ink)
                .width('100%')
                .margin({ bottom: 10 })

              TextArea({ placeholder: '说说这次服务的感受，比如服务人员的细致程度、沟通情况…' })
                .maxLength(500)
                .height(110)
                .fontSize(FontSize.minor)
                .fontColor(Palette.ink)
                .placeholderColor('#BDB6AE')
                .backgroundColor(Palette.bg)
                .borderRadius(10)
                .padding(10)
                .width('100%')
                .onChange((value: string) => {
                  this.content = value;
                })

              Text(this.content.length.toString() + '/500')
                .fontSize(10)
                .fontColor(Palette.ink3)
                .width('100%')
                .textAlign(TextAlign.End)
                .margin({ top: 4 })

              // 图片上传本期不做：后端只有 /system/smoke/file，prod 下 denyAll，
              // 没有正式的客户文件上传接口。这里给一行说明而不是放个点不动的假按钮。
              Row() {
                NavIcon({ path: IconPath.CAMERA, color: '#C9C2BA', size: 16, weight: 1.6 })
                Text('图片上传将在接入正式文件接口后开放')
                  .fontSize(10.5)
                  .fontColor(Palette.ink3)
                  .margin({ left: 6 })
              }
              .width('100%')
              .padding({ top: 8 })
            }
            .width('100%')
            .padding(Space.cardPad)
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.card)
            .margin({ top: 10 })
            .alignItems(HorizontalAlign.Start)

            // 匿名
            Row() {
              Column() {
                Text('匿名评价')
                  .fontSize(FontSize.body)
                  .fontColor(Palette.ink)
                Text('开启后其他用户看不到你的昵称')
                  .fontSize(FontSize.tiny)
                  .fontColor(Palette.ink3)
                  .margin({ top: 3 })
              }
              .alignItems(HorizontalAlign.Start)
              .layoutWeight(1)

              Toggle({ type: ToggleType.Switch, isOn: this.anonymous })
                .selectedColor(Palette.brand)
                .onChange((isOn: boolean) => {
                  this.anonymous = isOn;
                })
            }
            .width('100%')
            .padding(Space.cardPad)
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.card)
            .margin({ top: 10 })

            Row().height(20)
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)

        Row() {
          Button(this.submitting ? '提交中…' : '提交评价')
            .width('100%')
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
      .width('100%')
      .height('100%')
      .backgroundColor(Palette.bg)
    }
    .hideTitleBar(true)
  }
}
