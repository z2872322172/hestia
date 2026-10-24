import promptAction from '@ohos.promptAction';

/** 统一轻提示。 */
export class Toast {
  static show(message: string): void {
    promptAction.showToast({
      message: message,
      duration: 2200,
      bottom: '18%'
    });
  }
}
