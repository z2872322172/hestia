import { FontSize, Palette, Radius, Space } from '../../common/Theme';
import { EmptyState } from '../../components/EmptyState';
import { IconPath } from '../../common/IconPath';
import { MESSAGE_FILTERS, MessageFilter, Notification } from '../../model/Message';
import { MessageApi } from '../../api/MessageApi';
import { ApiClient } from '../../api/ApiClient';
import { MockData } from '../../mock/MockData';
import { Format } from '../../common/Format';
import { Toast } from '../../common/Toast';
import { Nav } from '../../common/Nav';
import { OrderDetailParam, RouteName } from '../../common/RouteName';

/** 05 消息 Tab。后端没有 businessType 参数，筛选只能对已加载数据本地过滤。 */
@Component
export struct MessageTab {
  @State list: Notification[] = [];
  @State filterIndex: number = 0;
  @State loading: boolean = true;
  @State refreshing: boolean = false;
  @StorageProp('tabIndex') @Watch('onTabChange') tabIndex: number = 0;

  aboutToAppear(): void {
    this.load();
  }

  onTabChange(): void {
    if (this.tabIndex === 2) {
      this.load();
    }
  }

  private async load(): Promise<void> {
    this.loading = this.list.length === 0;
    try {
      const page = await MessageApi.list(false, 1, 50);
      this.list = page.records;
    } catch (err) {
      if (ApiClient.isBusinessError(err as Object)) {
        Toast.show(ApiClient.messageOf(err as Object));
      } else {
        this.list = MockData.notifications();
      }
    }
    this.loading = false;
    this.refreshing = false;
    this.syncUnread();
  }

  private syncUnread(): void {
    let count: number = 0;
    for (let i = 0; i < this.list.length; i++) {
      if (this.list[i].readAt === undefined || this.list[i].readAt === null ||
        this.list[i].readAt.length === 0) {
        count = count + 1;
      }
    }
    AppStorage.setOrCreate('unreadCount', count);
  }

  private visibleList(): Notification[] {
    const filter: MessageFilter = MESSAGE_FILTERS[this.filterIndex];
    if (filter.businessType.length === 0) {
      return this.list;
    }
    const result: Notification[] = [];
    for (let i = 0; i < this.list.length; i++) {
      if (this.list[i].businessType === filter.businessType) {
        result.push(this.list[i]);
      }
    }
    return result;
  }

  private isUnread(item: Notification): boolean {
    return item.readAt === undefined || item.readAt === null || item.readAt.length === 0;
  }

  /** 用 JSON 深拷贝替换单条，避免直接改数组元素导致 @State 不刷新。 */
  private replaceReadAt(notificationNo: string, readAt: string): void {
    const next: Notification[] = [];
    for (let i = 0; i < this.list.length; i++) {
      const item: Notification = this.list[i];
      if (item.notificationNo === notificationNo) {
        const copy: Notification = JSON.parse(JSON.stringify(item)) as Notification;
        copy.readAt = readAt;
        next.push(copy);
      } else {
        next.push(item);
      }
    }
    this.list = next;
    this.syncUnread();
  }

  private async openMessage(item: Notification): Promise<void> {
    if (this.isUnread(item)) {
      try {
        await MessageApi.markRead(item.notificationNo);
        this.replaceReadAt(item.notificationNo, new Date().toISOString());
      } catch (err) {
        // 标已读失败不阻断跳转
      }
    }
    if (item.businessType === 'BOOKING' && item.businessId.length > 0) {
      const param: OrderDetailParam = { orderNo: item.businessId };
      Nav.push(RouteName.ORDER_DETAIL, param);
    }
  }

  private async readAll(): Promise<void> {
    try {
      await MessageApi.readAll();
      const next: Notification[] = [];
      const now: string = new Date().toISOString();
      for (let i = 0; i < this.list.length; i++) {
        const copy: Notification = JSON.parse(JSON.stringify(this.list[i])) as Notification;
        if (this.isUnread(copy)) {
          copy.readAt = now;
        }
        next.push(copy);
      }
      this.list = next;
      this.syncUnread();
      Toast.show('已全部标为已读');
    } catch (err) {
      Toast.show(ApiClient.messageOf(err as Object));
    }
  }

  build() {
    Column() {
      Row().height(44)

      Row() {
        Text('消息')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(Palette.ink)
        Blank()
        Text('全部已读')
          .fontSize(FontSize.minor)
          .fontColor(Palette.brand)
          .onClick(() => {
            this.readAll();
          })
      }
      .width('100%')
      .padding({ left: Space.page, right: Space.page, top: 6, bottom: 10 })

      // 筛选
      Row() {
        ForEach(MESSAGE_FILTERS, (filter: MessageFilter, index: number) => {
          Text(filter.label)
            .fontSize(FontSize.minor)
            .fontColor(this.filterIndex === index ? Palette.brandDeep : Palette.ink2)
            .backgroundColor(this.filterIndex === index ? Palette.brandXLight : Palette.grayBg)
            .borderRadius(8)
            .padding({ left: 12, right: 12, top: 6, bottom: 6 })
            .margin({ right: 8 })
            .onClick(() => {
              this.filterIndex = index;
            })
        }, (filter: MessageFilter) => filter.label)
        Blank()
      }
      .width('100%')
      .padding({ left: Space.page, right: Space.page, bottom: 10 })

      Refresh({ refreshing: $$this.refreshing }) {
        if (this.loading) {
          Column() {
            LoadingProgress()
              .width(32)
              .height(32)
              .color(Palette.brand)
          }
          .width('100%')
          .height(240)
          .justifyContent(FlexAlign.Center)
        } else if (this.visibleList().length === 0) {
          EmptyState({
            title: '暂无消息',
            desc: '订单状态变化会在这里通知你',
            iconPath: IconPath.MESSAGE
          })
        } else {
          List({ space: 10 }) {
            ForEach(this.visibleList(), (item: Notification) => {
              ListItem() {
                Row() {
                  Column() {
                    if (this.isUnread(item)) {
                      Circle()
                        .width(7)
                        .height(7)
                        .fill(Palette.danger)
                    } else {
                      Circle()
                        .width(7)
                        .height(7)
                        .fill(Color.Transparent)
                    }
                  }
                  .width(16)
                  .padding({ top: 6 })
                  .alignItems(HorizontalAlign.Center)

                  Column() {
                    Row() {
                      Text(item.title)
                        .fontSize(FontSize.cardTitle)
                        .fontWeight(this.isUnread(item) ? FontWeight.Medium : FontWeight.Normal)
                        .fontColor(Palette.ink)
                        .layoutWeight(1)
                        .maxLines(1)
                        .textOverflow({ overflow: TextOverflow.Ellipsis })
                      Text(Format.dateTime(item.createdAt))
                        .fontSize(FontSize.tiny)
                        .fontColor(Palette.ink3)
                        .margin({ left: 8 })
                    }
                    .width('100%')

                    Text(item.content)
                      .fontSize(FontSize.minor)
                      .fontColor(Palette.ink2)
                      .maxLines(2)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                      .width('100%')
                      .margin({ top: 5 })

                    if (item.businessType === 'BOOKING') {
                      Row() {
                        Text('查看订单')
                          .fontSize(FontSize.tag)
                          .fontColor(Palette.brandDeep)
                          .backgroundColor(Palette.brandXLight)
                          .borderRadius(Radius.tag)
                          .padding({ left: 7, right: 7, top: 2, bottom: 2 })
                      }
                      .margin({ top: 8 })
                    }
                  }
                  .layoutWeight(1)
                  .alignItems(HorizontalAlign.Start)
                }
                .width('100%')
                .padding(Space.cardPad)
                .backgroundColor(Palette.surface)
                .borderRadius(Radius.card)
                .alignItems(VerticalAlign.Top)
                .onClick(() => {
                  this.openMessage(item);
                })
              }
            }, (item: Notification) => item.notificationNo + item.readAt)

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
      .onRefreshing(() => {
        this.load();
      })
      .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Palette.bg)
  }
}
