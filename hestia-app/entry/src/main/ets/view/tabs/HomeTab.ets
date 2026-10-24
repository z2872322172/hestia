import { FontSize, Layout, Palette, Radius, Space, categoryBg, categoryIconColor } from '../../common/Theme';
import { IconPath } from '../../common/IconPath';
import { NavIcon } from '../../components/NavIcon';
import { PriceText } from '../../components/PriceText';
import { SectionHeader } from '../../components/SectionHeader';
import { Category, ServiceItemSummary } from '../../model/Catalog';
import { Address } from '../../model/Customer';
import { CatalogApi } from '../../api/CatalogApi';
import { CustomerApi } from '../../api/CustomerApi';
import { ApiClient } from '../../api/ApiClient';
import { MockData } from '../../mock/MockData';
import { Nav } from '../../common/Nav';
import { RouteName, ServiceDetailParam, ServiceListParam } from '../../common/RouteName';
import { Toast } from '../../common/Toast';

/** 03 首页。分类宫格与热门服务都来自真实接口，不是写死的。 */
@Component
export struct HomeTab {
  onOpenMessage: () => void = () => {
  };

  @State categories: Category[] = [];
  @State hotItems: ServiceItemSummary[] = [];
  @State cityText: string = '广州市 · 天河区';
  @State loading: boolean = true;
  @State degraded: boolean = false;
  @StorageProp('tabIndex') @Watch('onTabChange') tabIndex: number = 0;
  private stars: number[] = [0, 1, 2, 3, 4];

  aboutToAppear(): void {
    this.load();
    this.loadDefaultAddress();
  }

  onTabChange(): void {
    if (this.tabIndex === 0 && this.categories.length === 0) {
      this.load();
    }
  }

  private async loadDefaultAddress(): Promise<void> {
    try {
      const list: Address[] = await CustomerApi.addresses();
      for (let i = 0; i < list.length; i++) {
        if (list[i].isDefault) {
          this.cityText = list[i].region.cityName + ' · ' + list[i].region.districtName;
        }
      }
    } catch (err) {
      // 地址拉不到就用默认文案，不打扰用户
    }
  }

  private async load(): Promise<void> {
    this.loading = true;
    try {
      this.categories = await CatalogApi.categories();
      const all: ServiceItemSummary[] = await CatalogApi.items('');
      this.hotItems = all.slice(0, 2);
      this.degraded = false;
      MockData.clearDegraded();
    } catch (err) {
      if (ApiClient.isBusinessError(err as Object)) {
        Toast.show(ApiClient.messageOf(err as Object));
      } else {
        // 只有网络异常才降级；业务错误必须让用户看见
        this.categories = MockData.categories();
        this.hotItems = MockData.serviceItems('').slice(0, 2);
        this.degraded = true;
        MockData.markDegraded();
      }
    }
    this.loading = false;
  }

  /** 宫格只放前 3 个真实分类，第 4 格固定为"全部分类"。 */
  private gridCategories(): Category[] {
    return this.categories.slice(0, 3);
  }

  private categoryIcon(index: number): string {
    if (index === 1) {
      return IconPath.SPARKLE;
    }
    if (index === 2) {
      return IconPath.GRID;
    }
    return IconPath.HOUSE_FILLED;
  }

  private openCategory(categoryNo: string, title: string): void {
    const param: ServiceListParam = { categoryNo: categoryNo, title: title };
    Nav.push(RouteName.SERVICE_LIST, param);
  }

  private openDetail(itemNo: string): void {
    const param: ServiceDetailParam = { itemNo: itemNo };
    Nav.push(RouteName.SERVICE_DETAIL, param);
  }

  @Builder
  guaranteeItem(icon: string, text: string) {
    Column() {
      NavIcon({ path: icon, color: '#C2592C', size: 21, weight: 1.6 })
      Text(text)
        .fontSize(10.5)
        .fontColor(Palette.ink2)
        .margin({ top: 6 })
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Center)
  }

  build() {
    Column() {
      // ===== 沉浸式主色头部 =====
      Column() {
        Row().height(Layout.statusBar)

        Row() {
          NavIcon({ path: IconPath.LOCATION, color: Color.White, size: 16, weight: 1.9 })
          Text(this.cityText)
            .fontSize(13.5)
            .fontWeight(FontWeight.Medium)
            .fontColor(Color.White)
            .margin({ left: 6 })
          NavIcon({ path: IconPath.CHEVRON, color: Color.White, size: 13, weight: 2.2 })
            .rotate({ angle: 90 })
            .margin({ left: 2 })
          Blank()
          Stack({ alignContent: Alignment.TopEnd }) {
            NavIcon({ path: IconPath.BELL, color: Color.White, size: 21, weight: 1.8 })
            Circle()
              .width(7)
              .height(7)
              .fill('#FFD84D')
              .margin({ right: -1, top: -1 })
          }
          .onClick(() => this.onOpenMessage())
        }
        .width('100%')
        .padding({ top: 4, bottom: 14 })

        // 搜索框
        Row() {
          NavIcon({ path: IconPath.SEARCH, color: '#B8B0A8', size: 16, weight: 2 })
          Text('搜索 日常保洁 / 深度保洁')
            .fontSize(13)
            .fontColor('#B8B0A8')
            .margin({ left: 8 })
            .layoutWeight(1)
          Text('搜索')
            .fontSize(11.5)
            .fontColor(Color.White)
            .fontWeight(FontWeight.Medium)
            .backgroundColor(Palette.brandDeep)
            .borderRadius(7)
            .padding({ left: 9, right: 9, top: 3, bottom: 3 })
        }
        .width('100%')
        .height(38)
        .backgroundColor(Color.White)
        .borderRadius(12)
        .padding({ left: 12, right: 6 })
        .onClick(() => this.openCategory('', '全部服务'))
      }
      .width('100%')
      .padding({ left: Space.page, right: Space.page, bottom: 20 })
      .backgroundColor(Palette.brand)
      .borderRadius({ bottomLeft: 24, bottomRight: 24 })

      // ===== 滚动内容 =====
      Scroll() {
        Column() {
          if (this.degraded) {
            Text('演示数据 · 后端未连接')
              .fontSize(10.5)
              .fontColor(Palette.warnText)
              .backgroundColor(Palette.warnBg)
              .borderRadius(6)
              .padding({ left: 8, right: 8, top: 3, bottom: 3 })
              .margin({ top: 10 })
          }

          // 分类宫格
          Column() {
            Row() {
              ForEach(this.gridCategories(), (cat: Category, index: number) => {
                Column() {
                  Stack() {
                    NavIcon({
                      path: this.categoryIcon(index),
                      color: categoryIconColor(index),
                      size: 22,
                      weight: 1.7
                    })
                  }
                  .width(44)
                  .height(44)
                  .backgroundColor(categoryBg(index))
                  .borderRadius(14)

                  Text(cat.categoryName)
                    .fontSize(11.5)
                    .fontColor('#4A4540')
                    .margin({ top: 7 })
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                }
                .layoutWeight(1)
                .onClick(() => this.openCategory(cat.categoryNo, cat.categoryName))
              }, (cat: Category) => cat.categoryNo)

              // 全部分类
              Column() {
                Stack() {
                  NavIcon({ path: IconPath.GRID, color: '#B08246', size: 22, weight: 1.7 })
                }
                .width(44)
                .height(44)
                .backgroundColor('#FAF5EE')
                .borderRadius(14)

                Text('全部分类')
                  .fontSize(11.5)
                  .fontColor('#4A4540')
                  .margin({ top: 7 })
              }
              .layoutWeight(1)
              .onClick(() => this.openCategory('', '全部服务'))
            }
            .width('100%')
            .padding({ top: 14, bottom: 14 })
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.card)
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page })
          .margin({ top: -12 })

          // 新客券
          Stack({ alignContent: Alignment.TopEnd }) {
            Circle()
              .width(96)
              .height(96)
              .fill('rgba(255,255,255,0.5)')
              .offset({ x: 16, y: -16 })

            Column() {
              Text('新客首单立减 30')
                .fontSize(16)
                .fontWeight(FontWeight.Bold)
                .fontColor('#7A3418')
              Text('首次下单自动抵扣，无门槛')
                .fontSize(11.5)
                .fontColor('#A2653F')
                .margin({ top: 5 })
              Row() {
                Text('立即领取')
                  .fontSize(11)
                  .fontColor(Color.White)
                  .fontWeight(FontWeight.Medium)
                NavIcon({ path: IconPath.CHEVRON, color: Color.White, size: 11, weight: 2.6 })
                  .margin({ left: 2 })
              }
              .backgroundColor(Palette.brand)
              .borderRadius(8)
              .padding({ left: 10, right: 8, top: 4, bottom: 4 })
              .margin({ top: 11 })
              .onClick(() => {
                Toast.show('新客券将在下单时自动抵扣');
              })
            }
            .alignItems(HorizontalAlign.Start)
            .width('100%')
            .padding({ left: 18, right: 18, top: 16, bottom: 16 })
          }
          .width('100%')
          .backgroundColor('#FFF0E7')
          .borderRadius(Radius.card)
          .margin({ top: 14 })

          // 热门服务
          SectionHeader({
            title: '热门服务',
            actionText: '全部',
            onAction: () => this.openCategory('', '全部服务')
          })

          Column() {
            if (this.loading) {
              Row() {
                LoadingProgress()
                  .width(28)
                  .height(28)
                  .color(Palette.brand)
              }
              .width('100%')
              .height(80)
              .justifyContent(FlexAlign.Center)
            } else {
              ForEach(this.hotItems, (item: ServiceItemSummary, index: number) => {
                Row() {
                  Stack() {
                    NavIcon({
                      path: index === 1 ? IconPath.SPARKLE : IconPath.HOUSE_FILLED,
                      color: categoryIconColor(index),
                      size: 30,
                      weight: 1.5
                    })
                  }
                  .width(64)
                  .height(64)
                  .backgroundColor(categoryBg(index))
                  .borderRadius(13)

                  Column() {
                    Text(item.itemName)
                      .fontSize(FontSize.cardTitle)
                      .fontWeight(FontWeight.Medium)
                      .fontColor(Palette.ink)
                      .maxLines(1)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                    Text(item.shortDescription)
                      .fontSize(FontSize.minor)
                      .fontColor(Palette.ink2)
                      .margin({ top: 3 })
                      .maxLines(1)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                    Row() {
                      Text(item.categoryName)
                        .fontSize(FontSize.tag)
                        .fontColor(Palette.brandDeep)
                        .backgroundColor(Palette.brandXLight)
                        .borderRadius(Radius.tag)
                        .padding({ left: 7, right: 7, top: 2, bottom: 2 })
                    }
                    .margin({ top: 6 })
                    Row() {
                      PriceText({
                        amount: item.basePrice,
                        suffix: '/' + item.serviceUnit
                      })
                      Blank()
                    }
                    .width('100%')
                    .margin({ top: 8 })
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
                .margin({ bottom: Space.cardGap })
                .onClick(() => this.openDetail(item.itemNo))
              }, (item: ServiceItemSummary) => item.itemNo)
            }
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page })

          // 服务保障
          SectionHeader({ title: '服务保障' })
          Column() {
            Row() {
              this.guaranteeItem(IconPath.SHIELD, '持证上岗')
              this.guaranteeItem(IconPath.CLOCK, '准时上门')
              this.guaranteeItem(IconPath.REWORK, '不满意返工')
              this.guaranteeItem(IconPath.GUARANTEE, '平台担保')
            }
            .width('100%')
            .padding({ top: 16, bottom: 16 })
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.card)
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page })

          // 真实评价
          // 后端没有公开的评价列表接口（只有 GET /customer/reviews 查自己的），
          // 这里用静态示例；后端补齐公开评价接口后替换本区块数据源。
          SectionHeader({ title: '真实评价' })
          Column() {
            Column() {
              Row() {
                Stack() {
                  Text('陈')
                    .fontSize(14)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(Color.White)
                }
                .width(40)
                .height(40)
                .backgroundColor('#F5A57E')
                .borderRadius(20)

                Column() {
                  Text('陈女士')
                    .fontSize(13)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(Palette.ink)
                  Row() {
                    ForEach(this.stars, (i: number) => {
                      NavIcon({ path: IconPath.STAR, color: Palette.brand, size: 11, filled: true })
                        .margin({ right: 2 })
                    }, (i: number) => 'star' + i.toString())
                  }
                  .margin({ top: 2 })
                }
                .alignItems(HorizontalAlign.Start)
                .layoutWeight(1)
                .margin({ left: 10 })

                Text('10-21')
                  .fontSize(FontSize.tiny)
                  .fontColor(Palette.ink3)
              }
              .width('100%')

              Text('阿姨很细致，厨房油烟机都拆开洗了，走之前还把垃圾带走。下次还约她。')
                .fontSize(FontSize.minor)
                .fontColor('#3A3532')
                .width('100%')
                .margin({ top: 9 })

              Row() {
                Text('日常保洁 · 两居室')
                  .fontSize(FontSize.tag)
                  .fontColor(Palette.ink2)
                  .backgroundColor(Palette.grayBg)
                  .borderRadius(Radius.tag)
                  .padding({ left: 7, right: 7, top: 2, bottom: 2 })
              }
              .margin({ top: 8 })
            }
            .width('100%')
            .padding(Space.cardPad)
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.card)
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page })

          Row().height(20)
        }
        .width('100%')
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Palette.bg)
  }
}
