import common from '@ohos.app.ability.common';
import { FontSize, Layout, Palette, Radius, Space } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import { EmptyState } from '../components/EmptyState';
import { Address, AddressRequest } from '../model/Customer';
import { areaMatchStyle } from '../model/Enums';
import { CustomerApi } from '../api/CustomerApi';
import { ApiClient } from '../api/ApiClient';
import { MockData } from '../mock/MockData';
import { Nav } from '../common/Nav';
import { AddressParam, RouteName } from '../common/RouteName';
import { Toast } from '../common/Toast';
import { Bar } from '../common/Bar';

/**
 * 13 地址管理。
 *
 * 后端没有省市区三级联动接口（只有 /public/service-areas 和 /public/service-area-match），
 * 所以本期把行政区固定为种子数据里的广州市天河区，用户只填详细地址。
 * 这是本期明确的功能裁剪，已在设计文档第 13 节登记。
 */
@Component
export struct AddressPage {
  param: AddressParam = { pickMode: false };

  @State list: Address[] = [];
  @State loading: boolean = true;
  @State sheetVisible: boolean = false;
  @State editing: Address | null = null;

  @State fLabel: string = '';
  @State fName: string = '';
  @State fPhone: string = '';
  @State fCommunity: string = '';
  @State fBuilding: string = '';
  @State fUnit: string = '';
  @State fFloor: string = '';
  @State fRoom: string = '';
  @State fDetail: string = '';
  @State fDefault: boolean = false;

  aboutToAppear(): void {
    Bar.light(getContext(this) as common.Context);
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = this.list.length === 0;
    try {
      this.list = await CustomerApi.addresses();
    } catch (err) {
      if (ApiClient.isBusinessError(err as Object)) {
        Toast.show(ApiClient.messageOf(err as Object));
      } else {
        this.list = MockData.addresses();
      }
    }
    this.loading = false;
  }

  private openCreate(): void {
    this.editing = null;
    this.fLabel = '';
    this.fName = '';
    this.fPhone = '';
    this.fCommunity = '';
    this.fBuilding = '';
    this.fUnit = '';
    this.fFloor = '';
    this.fRoom = '';
    this.fDetail = '';
    this.fDefault = this.list.length === 0;
    this.sheetVisible = true;
  }

  private openEdit(item: Address): void {
    this.editing = item;
    this.fLabel = item.label;
    this.fName = item.contactName;
    this.fPhone = item.contactPhone;
    this.fCommunity = item.communityName;
    this.fBuilding = item.buildingNo;
    this.fUnit = item.unitNo;
    this.fFloor = item.floorNo;
    this.fRoom = item.roomNo;
    this.fDetail = item.detailAddress;
    this.fDefault = item.isDefault;
    this.sheetVisible = true;
  }

  private closeSheet(): void {
    this.sheetVisible = false;
    this.editing = null;
  }

  private async save(): Promise<void> {
    if (this.fName.trim().length === 0) {
      Toast.show('请填写联系人');
      return;
    }
    if (!new RegExp('^1[3-9]\\d{9}$').test(this.fPhone)) {
      Toast.show('请填写正确的手机号');
      return;
    }
    if (this.fDetail.trim().length === 0) {
      Toast.show('请填写详细地址');
      return;
    }

    const req: AddressRequest = {
      label: this.fLabel.length > 0 ? this.fLabel : '家',
      contactName: this.fName.trim(),
      contactCountryCode: '86',
      contactPhone: this.fPhone,
      cityCode: '440100',
      districtCode: '440106',
      communityName: this.fCommunity,
      buildingNo: this.fBuilding,
      unitNo: this.fUnit,
      floorNo: this.fFloor,
      roomNo: this.fRoom,
      detailAddress: this.fDetail.trim(),
      accessNote: '',
      isDefault: this.fDefault
    };
    if (this.editing !== null) {
      req.version = this.editing.version;
    }

    try {
      if (this.editing === null) {
        await CustomerApi.createAddress(req);
        Toast.show('地址已添加');
      } else {
        await CustomerApi.updateAddress(this.editing.addressNo, req);
        Toast.show('地址已更新');
      }
      this.closeSheet();
      this.load();
    } catch (err) {
      Toast.show(ApiClient.messageOf(err as Object));
    }
  }

  private async remove(item: Address): Promise<void> {
    try {
      await CustomerApi.deleteAddress(item.addressNo);
      Toast.show('地址已删除');
      this.load();
    } catch (err) {
      Toast.show(ApiClient.messageOf(err as Object));
    }
  }

  private async setDefault(item: Address): Promise<void> {
    try {
      await CustomerApi.setDefault(item.addressNo);
      Toast.show('已设为默认地址');
      this.load();
    } catch (err) {
      Toast.show(ApiClient.messageOf(err as Object));
    }
  }

  private async checkArea(item: Address): Promise<void> {
    try {
      await CustomerApi.availabilityCheck(item.addressNo);
      Toast.show('已重新校验服务范围');
      this.load();
    } catch (err) {
      Toast.show(ApiClient.messageOf(err as Object));
    }
  }

  private onCardTap(item: Address): void {
    if (this.param.pickMode !== undefined && this.param.pickMode) {
      this.setDefault(item).then(() => {
        Nav.back();
      });
    }
  }

  @Builder
  inputRow(label: string, placeholder: string, value: string, onChange: (v: string) => void) {
    Row() {
      Text(label)
        .fontSize(FontSize.minor)
        .fontColor(Palette.ink2)
        .width(64)
      TextInput({ placeholder: placeholder, text: value })
        .fontSize(FontSize.minor)
        .fontColor(Palette.ink)
        .placeholderColor('#BDB6AE')
        .backgroundColor(Palette.bg)
        .borderRadius(8)
        .height(40)
        .layoutWeight(1)
        .onChange(onChange)
    }
    .width('100%')
    .margin({ bottom: 8 })
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  addressCard(item: Address) {
    Column() {
      Row() {
        if (item.label.length > 0) {
          Text(item.label)
            .fontSize(9.5)
            .fontColor(Palette.brandDeep)
            .backgroundColor(Palette.brandXLight)
            .borderRadius(4)
            .padding({ left: 5, right: 5, top: 1, bottom: 1 })
            .margin({ right: 6 })
        }
        Text(item.contactName)
          .fontSize(FontSize.body)
          .fontWeight(FontWeight.Medium)
          .fontColor(Palette.ink)
        Text(item.contactPhone)
          .fontSize(FontSize.minor)
          .fontColor(Palette.ink2)
          .margin({ left: 8 })
        Blank()
        if (item.isDefault) {
          Text('默认')
            .fontSize(9.5)
            .fontColor(Palette.okText)
            .backgroundColor(Palette.okBg)
            .borderRadius(4)
            .padding({ left: 5, right: 5, top: 1, bottom: 1 })
        }
      }
      .width('100%')

      Text(item.detailAddress)
        .fontSize(FontSize.minor)
        .fontColor(Palette.ink2)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')
        .margin({ top: 6 })

      if (areaMatchStyle(item.areaMatchStatus).text.length > 0) {
        Text(areaMatchStyle(item.areaMatchStatus).text)
          .fontSize(9.5)
          .fontColor(areaMatchStyle(item.areaMatchStatus).color)
          .backgroundColor(areaMatchStyle(item.areaMatchStatus).bg)
          .borderRadius(4)
          .padding({ left: 5, right: 5, top: 1, bottom: 1 })
          .margin({ top: 6 })
      }

      Divider().color(Palette.line).margin({ top: 10, bottom: 8 })

      Row() {
        Text('设为默认')
          .fontSize(FontSize.tiny)
          .fontColor(item.isDefault ? Palette.ink3 : Palette.ink2)
          .onClick(() => {
            if (!item.isDefault) {
              this.setDefault(item);
            }
          })
        Row().width(14)
        Text('校验范围')
          .fontSize(FontSize.tiny)
          .fontColor(Palette.ink2)
          .onClick(() => this.checkArea(item))
        Row().width(14)
        Text('编辑')
          .fontSize(FontSize.tiny)
          .fontColor(Palette.ink2)
          .onClick(() => this.openEdit(item))
        Row().width(14)
        Text('删除')
          .fontSize(FontSize.tiny)
          .fontColor(Palette.danger)
          .onClick(() => this.remove(item))
        Blank()
      }
      .width('100%')
    }
    .width('100%')
    .padding(Space.cardPad)
    .backgroundColor(Palette.surface)
    .borderRadius(Radius.card)
    .alignItems(HorizontalAlign.Start)
    .onClick(() => this.onCardTap(item))
  }

  build() {
    NavDestination() {
      Stack({ alignContent: Alignment.Bottom }) {
        Column() {
          Row().height(Layout.statusBar)

          Row() {
            Row() {
              NavIcon({ path: IconPath.BACK, color: Palette.ink, size: 22, weight: 2 })
            }
            .width(40)
            .height(40)
            .onClick(() => Nav.back())

            Text(this.param.pickMode !== undefined && this.param.pickMode ? '选择地址' : '地址管理')
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
          } else if (this.list.length === 0) {
            Column() {
              EmptyState({
                title: '还没有服务地址',
                desc: '添加地址后才能预约上门服务',
                actionText: '添加地址',
                iconPath: IconPath.LOCATION,
                onAction: () => this.openCreate()
              })
            }
            .width('100%')
            .layoutWeight(1)
          } else {
            List({ space: 10 }) {
              ForEach(this.list, (item: Address) => {
                ListItem() {
                  this.addressCard(item)
                }
              }, (item: Address) => item.addressNo + item.version.toString())

              ListItem() {
                Row().height(16)
              }
            }
            .width('100%')
            .padding({ left: Space.page, right: Space.page })
            .scrollBar(BarState.Off)
            .layoutWeight(1)
          }

          Row() {
            Button('新增地址')
              .width('100%')
              .height(Layout.btnHeight)
              .fontSize(15)
              .fontWeight(FontWeight.Medium)
              .fontColor(Color.White)
              .backgroundColor(Palette.brand)
              .borderRadius(Radius.btn)
              .onClick(() => this.openCreate())
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page, top: 10, bottom: 14 })
          .backgroundColor(Palette.surface)
          .border({ width: { top: 1 }, color: Palette.line })
        }
        .width('100%')
        .height('100%')

        // ===== 编辑弹层 =====
        if (this.sheetVisible) {
          Column()
            .width('100%')
            .height('100%')
            .backgroundColor('rgba(0,0,0,0.35)')
            .onClick(() => this.closeSheet())

          Column() {
            Row() {
              Text(this.editing === null ? '新增地址' : '编辑地址')
                .fontSize(16)
                .fontWeight(FontWeight.Bold)
                .fontColor(Palette.ink)
                .layoutWeight(1)
              Text('关闭')
                .fontSize(FontSize.minor)
                .fontColor(Palette.ink3)
                .onClick(() => this.closeSheet())
            }
            .width('100%')
            .margin({ bottom: 14 })

            Scroll() {
              Column() {
                this.inputRow('联系人', '收货人姓名', this.fName, (v: string) => {
                  this.fName = v;
                })
                this.inputRow('手机号', '11 位手机号', this.fPhone, (v: string) => {
                  this.fPhone = v;
                })
                this.inputRow('标签', '家 / 公司', this.fLabel, (v: string) => {
                  this.fLabel = v;
                })
                this.inputRow('小区', '小区或写字楼名称', this.fCommunity, (v: string) => {
                  this.fCommunity = v;
                })
                this.inputRow('楼栋', '如 3 栋', this.fBuilding, (v: string) => {
                  this.fBuilding = v;
                })
                this.inputRow('单元', '如 2 单元', this.fUnit, (v: string) => {
                  this.fUnit = v;
                })
                this.inputRow('楼层', '如 5 层', this.fFloor, (v: string) => {
                  this.fFloor = v;
                })
                this.inputRow('门牌', '如 502', this.fRoom, (v: string) => {
                  this.fRoom = v;
                })
                this.inputRow('详细地址', '广州市天河区…', this.fDetail, (v: string) => {
                  this.fDetail = v;
                })

                Row() {
                  Text('设为默认地址')
                    .fontSize(FontSize.minor)
                    .fontColor(Palette.ink)
                    .layoutWeight(1)
                  Toggle({ type: ToggleType.Switch, isOn: this.fDefault })
                    .selectedColor(Palette.brand)
                    .onChange((isOn: boolean) => {
                      this.fDefault = isOn;
                    })
                }
                .width('100%')
                .margin({ top: 4, bottom: 12 })

                Text('行政区固定为广州市天河区（后端暂无三级联动接口）')
                  .fontSize(10)
                  .fontColor(Palette.ink3)
                  .width('100%')
                  .margin({ bottom: 10 })

                Button('保存')
                  .width('100%')
                  .height(Layout.btnHeight)
                  .fontSize(15)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(Color.White)
                  .backgroundColor(Palette.brand)
                  .borderRadius(Radius.btn)
                  .onClick(() => this.save())
              }
              .width('100%')
            }
            .constraintSize({ maxHeight: 420 })
            .scrollBar(BarState.Off)
          }
          .width('100%')
          .padding({ left: Space.page, right: Space.page, top: 16, bottom: 20 })
          .backgroundColor(Palette.surface)
          .borderRadius({ topLeft: Radius.sheet, topRight: Radius.sheet })
        }
      }
      .width('100%')
      .height('100%')
    }
    .hideTitleBar(true)
  }
}
