import { FontSize, Layout, Palette, Radius, Space } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import { ServiceCard } from '../components/ServiceCard';
import { EmptyState } from '../components/EmptyState';
import { Category, ServiceItemSummary } from '../model/Catalog';
import { CatalogApi } from '../api/CatalogApi';
import { ApiClient } from '../api/ApiClient';
import { MockData } from '../mock/MockData';
import { Nav } from '../common/Nav';
import { RouteName, ServiceDetailParam, ServiceListParam } from '../common/RouteName';
import { Bar } from '../common/Bar';
import { Toast } from '../common/Toast';
import common from '@ohos.app.ability.common';

/** 07 服务列表。排序是本地做的：后端 /public/service-items 没有排序参数。 */
@Component
export struct ServiceListPage {
  param: ServiceListParam = { categoryNo: '', title: '全部服务' };

  @State categories: Category[] = [];
  @State items: ServiceItemSummary[] = [];
  @State activeCategory: string = '';
  @State sortBy: string = 'default';
  @State loading: boolean = true;

  aboutToAppear(): void {
    Bar.light(getContext(this) as common.Context);
    if (this.param.categoryNo !== undefined && this.param.categoryNo !== null) {
      this.activeCategory = this.param.categoryNo;
    }
    this.loadCategories();
    this.loadItems();
  }

  private async loadCategories(): Promise<void> {
    try {
      this.categories = await CatalogApi.categories();
    } catch (err) {
      this.categories = MockData.categories();
    }
  }

  private async loadItems(): Promise<void> {
    this.loading = true;
    try {
      this.items = await CatalogApi.items(this.activeCategory);
    } catch (err) {
      if (ApiClient.isBusinessError(err as Object)) {
        Toast.show(ApiClient.messageOf(err as Object));
      } else {
        this.items = MockData.serviceItems(this.activeCategory);
      }
    }
    this.loading = false;
  }

  /** 综合不排序，价格按时长升序，都是本地算，数据量小。 */
  private sorted(): ServiceItemSummary[] {
    const copy: ServiceItemSummary[] = this.items.slice();
    if (this.sortBy === 'price') {
      copy.sort((a: ServiceItemSummary, b: ServiceItemSummary) => a.basePrice - b.basePrice);
    } else if (this.sortBy === 'duration') {
      copy.sort((a: ServiceItemSummary, b: ServiceItemSummary) =>
        a.durationMinutes - b.durationMinutes);
    }
    return copy;
  }

  private title(): string {
    if (this.param.title !== undefined && this.param.title !== null && this.param.title.length > 0) {
      return this.param.title;
    }
    return '全部服务';
  }

  private openDetail(itemNo: string): void {
    const param: ServiceDetailParam = { itemNo: itemNo };
    Nav.push(RouteName.SERVICE_DETAIL, param);
  }

  @Builder
  sortChip(label: string, key: string) {
    Text(label)
      .fontSize(FontSize.minor)
      .fontColor(this.sortBy === key ? Palette.brand : Palette.ink2)
      .fontWeight(this.sortBy === key ? FontWeight.Medium : FontWeight.Normal)
      .onClick(() => {
        this.sortBy = key;
      })
  }

  build() {
    NavDestination() {
      Column() {
        // 导航栏（状态栏占位 + 52 高标题栏）
        Row().height(Layout.statusBar)

        Row() {
          Row() {
            NavIcon({ path: IconPath.BACK, color: Palette.ink, size: 22, weight: 2 })
          }
          .width(40)
          .height(40)
          .justifyContent(FlexAlign.Start)
          .onClick(() => Nav.back())

          Text(this.title())
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
        .backgroundColor(Palette.bg)

        // 分类条
        Scroll() {
          Row() {
            Text('全部')
              .fontSize(FontSize.minor)
              .fontColor(this.activeCategory.length === 0 ? Color.White : Palette.ink2)
              .backgroundColor(this.activeCategory.length === 0 ? Palette.brand : Palette.surface)
              .borderRadius(9)
              .padding({ left: 13, right: 13, top: 7, bottom: 7 })
              .margin({ right: 8 })
              .onClick(() => {
                this.activeCategory = '';
                this.loadItems();
              })

            ForEach(this.categories, (cat: Category) => {
              Text(cat.categoryName)
                .fontSize(FontSize.minor)
                .fontColor(this.activeCategory === cat.categoryNo ? Color.White : Palette.ink2)
                .backgroundColor(this.activeCategory === cat.categoryNo ? Palette.brand : Palette.surface)
                .borderRadius(9)
                .padding({ left: 13, right: 13, top: 7, bottom: 7 })
                .margin({ right: 8 })
                .onClick(() => {
                  this.activeCategory = cat.categoryNo;
                  this.loadItems();
                })
            }, (cat: Category) => cat.categoryNo)
          }
          .padding({ left: Space.page, right: Space.page })
        }
        .width('100%')
        .scrollable(ScrollDirection.Horizontal)
        .scrollBar(BarState.Off)
        .padding({ bottom: 10 })

        // 排序行
        Row() {
          this.sortChip('综合', 'default')
          Row().width(18)
          this.sortChip('价格', 'price')
          Row().width(18)
          this.sortChip('时长', 'duration')
          Blank()
          Text(this.items.length.toString() + ' 项服务')
            .fontSize(FontSize.tiny)
            .fontColor(Palette.ink3)
        }
        .width('100%')
        .padding({ left: Space.page, right: Space.page, bottom: 10 })

        // 列表
        if (this.loading) {
          Column() {
            LoadingProgress()
              .width(32)
              .height(32)
              .color(Palette.brand)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else if (this.items.length === 0) {
          Column() {
            EmptyState({
              title: '该分类暂无服务',
              desc: '这个分类下还没有已上架的服务项，看看其他分类吧',
              actionText: '查看全部',
              iconPath: IconPath.GRID,
              onAction: () => {
                this.activeCategory = '';
                this.loadItems();
              }
            })
          }
          .width('100%')
          .layoutWeight(1)
        } else {
          List({ space: 10 }) {
            ForEach(this.sorted(), (item: ServiceItemSummary, index: number) => {
              ListItem() {
                ServiceCard({
                  item: item,
                  index: index,
                  onTap: () => {
                    this.openDetail(item.itemNo);
                  }
                })
              }
            }, (item: ServiceItemSummary) => item.itemNo + this.sortBy)

            ListItem() {
              Row().height(16)
            }
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page })
          .scrollBar(BarState.Off)
          .layoutWeight(1)
        }
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Palette.bg)
    }
    .hideTitleBar(true)
  }
}
