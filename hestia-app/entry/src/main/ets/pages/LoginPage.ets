import { FontSize, Layout, Palette, Radius, Space } from '../common/Theme';
import { IconPath } from '../common/IconPath';
import { NavIcon } from '../components/NavIcon';
import { Nav } from '../common/Nav';
import { RouteName } from '../common/RouteName';
import { Storage } from '../common/Storage';
import { Bar } from '../common/Bar';
import { AuthApi } from '../api/AuthApi';
import { ApiClient } from '../api/ApiClient';
import { LoginResult } from '../model/Customer';
import { Toast } from '../common/Toast';
import common from '@ohos.app.ability.common';

/** 02 登录 / 注册。注册成功后端直接返回登录态，处理方式与登录一致。 */
@Component
export struct LoginPage {
  @State isRegister: boolean = false;
  @State phone: string = '';
  @State password: string = '';
  @State displayName: string = '';
  @State remember: boolean = true;
  @State agree: boolean = false;
  @State showPassword: boolean = false;
  @State submitting: boolean = false;
  @State errorMsg: string = '';

  aboutToAppear(): void {
    Bar.light(getContext(this) as common.Context);
    this.loadRemembered();
  }

  private async loadRemembered(): Promise<void> {
    const saved: string = await Storage.getRememberPhone();
    if (saved.length > 0) {
      this.phone = saved;
      this.remember = true;
    }
  }

  /** 前端先拦，减少无效请求。后端仍会二次校验。 */
  private validate(): string {
    if (!new RegExp('^1[3-9]\\d{9}$').test(this.phone)) {
      return '请输入正确的手机号';
    }
    if (this.password.length < 6) {
      return '密码至少 6 位';
    }
    if (this.isRegister && this.displayName.trim().length === 0) {
      return '请填写昵称';
    }
    if (!this.agree) {
      return '请先阅读并同意服务协议';
    }
    return '';
  }

  private async submit(): Promise<void> {
    if (this.submitting) {
      return;
    }
    const invalid: string = this.validate();
    if (invalid.length > 0) {
      this.errorMsg = invalid;
      return;
    }

    this.submitting = true;
    this.errorMsg = '';
    try {
      let result: LoginResult;
      if (this.isRegister) {
        result = await AuthApi.register(this.phone, this.password, this.displayName.trim());
      } else {
        result = await AuthApi.login(this.phone, this.password);
      }
      await Storage.setToken(result.accessToken);
      await Storage.setAccountJson(JSON.stringify(result.account));
      if (this.remember) {
        await Storage.setRememberPhone(this.phone);
      } else {
        await Storage.putString('remember_phone', '');
      }
      Toast.show('登录成功');
      Nav.reset(RouteName.MAIN);
    } catch (err) {
      this.errorMsg = ApiClient.messageOf(err as Object);
    } finally {
      this.submitting = false;
    }
  }

  @Builder
  fieldLabel(text: string, topMargin: number) {
    Text(text)
      .fontSize(FontSize.minor)
      .fontColor(Palette.ink2)
      .width('100%')
      .margin({ top: topMargin, bottom: 6 })
  }

  build() {
    Column() {
      // 顶部留白（全屏布局后内容会顶到状态栏）
      Row().height(Layout.statusBar)

      Scroll() {
        Column() {
          Text(this.isRegister ? '创建账号' : '欢迎回来')
            .fontSize(FontSize.h1)
            .fontWeight(FontWeight.Bold)
            .fontColor(Palette.ink)
            .width('100%')

          Text(this.isRegister ? '注册后即可预约上门服务' : '登录后即可预约上门服务')
            .fontSize(FontSize.body)
            .fontColor(Palette.ink2)
            .width('100%')
            .margin({ top: 8, bottom: 26 })

          // 手机号
          this.fieldLabel('手机号', 0)
          Row() {
            Text('+86')
              .fontSize(14)
              .fontColor(Palette.ink)
              .padding({ right: 10 })
            Divider()
              .vertical(true)
              .height(18)
              .color(Palette.line)
            TextInput({ placeholder: '请输入手机号', text: this.phone })
              .type(InputType.PhoneNumber)
              .maxLength(11)
              .placeholderColor('#BDB6AE')
              .placeholderFont({ size: 14 })
              .fontSize(14)
              .fontColor(Palette.ink)
              .backgroundColor(Color.Transparent)
              .padding({ left: 10 })
              .layoutWeight(1)
              .onChange((value: string) => {
                this.phone = value;
                this.errorMsg = '';
              })
          }
          .width('100%')
          .height(50)
          .backgroundColor(Palette.surface)
          .borderRadius(Radius.btn)
          .padding({ left: 14, right: 14 })
          .alignItems(VerticalAlign.Center)

          // 昵称（仅注册）
          if (this.isRegister) {
            this.fieldLabel('昵称', 16)
            Row() {
              TextInput({ placeholder: '怎么称呼您', text: this.displayName })
                .maxLength(20)
                .placeholderColor('#BDB6AE')
                .placeholderFont({ size: 14 })
                .fontSize(14)
                .fontColor(Palette.ink)
                .backgroundColor(Color.Transparent)
                .layoutWeight(1)
                .onChange((value: string) => {
                  this.displayName = value;
                  this.errorMsg = '';
                })
            }
            .width('100%')
            .height(50)
            .backgroundColor(Palette.surface)
            .borderRadius(Radius.btn)
            .padding({ left: 14, right: 14 })
          }

          // 密码
          this.fieldLabel('密码', 16)
          Row() {
            TextInput({ placeholder: '请输入密码', text: this.password })
              .type(this.showPassword ? InputType.Normal : InputType.Password)
              .maxLength(32)
              .placeholderColor('#BDB6AE')
              .placeholderFont({ size: 14 })
              .fontSize(14)
              .fontColor(Palette.ink)
              .backgroundColor(Color.Transparent)
              .layoutWeight(1)
              .onChange((value: string) => {
                this.password = value;
                this.errorMsg = '';
              })
            NavIcon({
              path: this.showPassword ? IconPath.EYE : IconPath.EYE_OFF,
              color: Palette.ink3,
              size: 20,
              weight: 1.6
            })
              .margin({ left: 8 })
              .onClick(() => {
                this.showPassword = !this.showPassword;
              })
          }
          .width('100%')
          .height(50)
          .backgroundColor(Palette.surface)
          .borderRadius(Radius.btn)
          .padding({ left: 14, right: 14 })
          .alignItems(VerticalAlign.Center)

          // 记住手机号
          Row() {
            Checkbox()
              .select(this.remember)
              .selectedColor(Palette.brand)
              .width(18)
              .height(18)
              .onChange((value: boolean) => {
                this.remember = value;
              })
            Text('记住手机号')
              .fontSize(FontSize.minor)
              .fontColor(Palette.ink2)
              .margin({ left: 6 })
            Blank()
          }
          .width('100%')
          .margin({ top: 14 })

          // 错误提示
          if (this.errorMsg.length > 0) {
            Text(this.errorMsg)
              .fontSize(FontSize.minor)
              .fontColor(Palette.danger)
              .width('100%')
              .margin({ top: 12 })
          }

          Button(this.submitting ? '处理中…' : (this.isRegister ? '注册并登录' : '登录'))
            .width('100%')
            .height(Layout.btnHeight)
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor(Color.White)
            .backgroundColor(this.submitting ? '#F5A98C' : Palette.brand)
            .borderRadius(Radius.btn)
            .enabled(!this.submitting)
            .margin({ top: 22 })
            .onClick(() => {
              this.submit();
            })

          Row() {
            Text(this.isRegister ? '已有账号？' : '还没有账号？')
              .fontSize(FontSize.minor)
              .fontColor(Palette.ink3)
            Text(this.isRegister ? '去登录' : '立即注册')
              .fontSize(FontSize.minor)
              .fontColor(Palette.brand)
              .fontWeight(FontWeight.Medium)
              .onClick(() => {
                this.isRegister = !this.isRegister;
                this.errorMsg = '';
              })
          }
          .margin({ top: 18 })

          // 协议
          Row() {
            Checkbox()
              .select(this.agree)
              .selectedColor(Palette.brand)
              .width(16)
              .height(16)
              .onChange((value: boolean) => {
                this.agree = value;
                this.errorMsg = '';
              })
            Text() {
              Span('我已阅读并同意')
                .fontColor(Palette.ink3)
              Span('《服务协议》')
                .fontColor(Palette.brand)
              Span('与')
                .fontColor(Palette.ink3)
              Span('《隐私政策》')
                .fontColor(Palette.brand)
            }
            .fontSize(11)
            .margin({ left: 6 })
            .layoutWeight(1)
          }
          .width('100%')
          .margin({ top: 20, bottom: 20 })
          .alignItems(VerticalAlign.Top)
        }
        .width('100%')
        .padding({ left: 24, right: 24, top: 40 })
        .constraintSize({ minHeight: Layout.screenH })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Palette.bg)
  }
}
