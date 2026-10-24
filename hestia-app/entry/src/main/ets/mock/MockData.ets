import { Category, ServiceItemDetail, ServiceItemSummary, StandardGroup } from '../model/Catalog';
import {
  Availability,
  BookingDetail,
  BookingSummary,
  Fulfillment,
  TimelineStep
} from '../model/Booking';
import { Address, Account } from '../model/Customer';
import { Notification } from '../model/Message';
import { Storage } from '../common/Storage';

/**
 * 后端不可用时的兜底数据。
 *
 * 只兜底**读**接口。写操作（登录/下单/支付/评价/地址增删改）绝不降级，
 * 否则会出现"界面显示下单成功但后端没有数据"的假象，答辩现场最致命。
 */
export class MockData {
  /** 标记当前处于演示数据状态，首页据此显示角标。 */
  static markDegraded(): void {
    Storage.setDegraded(true);
  }

  static clearDegraded(): void {
    Storage.setDegraded(false);
  }

  static categories(): Category[] {
    const list: Category[] = [
      {
        categoryNo: 'CAT_CLEAN_DAILY',
        categoryName: '日常保洁',
        iconUrl: '',
        description: '按次上门的基础清洁',
        sortOrder: 10,
        status: 'ACTIVE',
        children: []
      },
      {
        categoryNo: 'CAT_CLEAN_DEEP',
        categoryName: '深度保洁',
        iconUrl: '',
        description: '厨卫重点深度清洁',
        sortOrder: 20,
        status: 'ACTIVE',
        children: []
      },
      {
        categoryNo: 'CAT_CLEAN_FIRST',
        categoryName: '开荒保洁',
        iconUrl: '',
        description: '新居入住前开荒',
        sortOrder: 30,
        status: 'ACTIVE',
        children: []
      },
      {
        categoryNo: 'CAT_APPLIANCE',
        categoryName: '家电清洗',
        iconUrl: '',
        description: '油烟机、空调、洗衣机清洗',
        sortOrder: 40,
        status: 'ACTIVE',
        children: []
      }
    ];
    return list;
  }

  static serviceItems(categoryNo: string): ServiceItemSummary[] {
    const all: ServiceItemSummary[] = [
      {
        itemNo: 'SVC_DAILY_2ROOM',
        itemName: '日常保洁 · 两居室',
        shortDescription: '80 平米以内 · 4 小时上门',
        coverUrl: '',
        categoryNo: 'CAT_CLEAN_DAILY',
        categoryName: '日常保洁',
        serviceUnit: '次',
        pricingMode: 'FIXED',
        basePrice: 199,
        durationMinutes: 240,
        version: 1,
        status: 'PUBLISHED'
      },
      {
        itemNo: 'SVC_DAILY_3ROOM',
        itemName: '日常保洁 · 三居室',
        shortDescription: '110 平米以内 · 5 小时上门',
        coverUrl: '',
        categoryNo: 'CAT_CLEAN_DAILY',
        categoryName: '日常保洁',
        serviceUnit: '次',
        pricingMode: 'FIXED',
        basePrice: 269,
        durationMinutes: 300,
        version: 1,
        status: 'PUBLISHED'
      },
      {
        itemNo: 'SVC_DAILY_1ROOM',
        itemName: '日常保洁 · 单间',
        shortDescription: '40 平米以内 · 2 小时上门',
        coverUrl: '',
        categoryNo: 'CAT_CLEAN_DAILY',
        categoryName: '日常保洁',
        serviceUnit: '次',
        pricingMode: 'FIXED',
        basePrice: 139,
        durationMinutes: 120,
        version: 1,
        status: 'PUBLISHED'
      },
      {
        itemNo: 'SVC_DEEP_AREA',
        itemName: '深度保洁 · 按面积',
        shortDescription: '含厨卫深度清洁 · 5 小时',
        coverUrl: '',
        categoryNo: 'CAT_CLEAN_DEEP',
        categoryName: '深度保洁',
        serviceUnit: '平米',
        pricingMode: 'TIERED',
        basePrice: 399,
        durationMinutes: 300,
        version: 1,
        status: 'PUBLISHED'
      },
      {
        itemNo: 'SVC_HOOD_CLEAN',
        itemName: '油烟机深度拆洗',
        shortDescription: '拆洗风轮 · 约 90 分钟',
        coverUrl: '',
        categoryNo: 'CAT_APPLIANCE',
        categoryName: '家电清洗',
        serviceUnit: '台',
        pricingMode: 'FIXED',
        basePrice: 120,
        durationMinutes: 90,
        version: 1,
        status: 'PUBLISHED'
      }
    ];
    if (categoryNo.length === 0) {
      return all;
    }
    const filtered: ServiceItemSummary[] = [];
    for (let i = 0; i < all.length; i++) {
      if (all[i].categoryNo === categoryNo) {
        filtered.push(all[i]);
      }
    }
    return filtered;
  }

  static detail(itemNo: string): ServiceItemDetail {
    const list: ServiceItemSummary[] = MockData.serviceItems('');
    let base: ServiceItemSummary = list[0];
    for (let i = 0; i < list.length; i++) {
      if (list[i].itemNo === itemNo) {
        base = list[i];
      }
    }
    const detail: ServiceItemDetail = {
      itemNo: base.itemNo,
      itemName: base.itemName,
      shortDescription: base.shortDescription,
      detailDescription: '服务人员自带专业工具与环保清洁剂，按标准作业流程完成全屋清洁，' +
        '服务结束后拍照留档，不满意可在 24 小时内申请返工。',
      coverUrl: '',
      categoryNo: base.categoryNo,
      categoryName: base.categoryName,
      serviceUnit: base.serviceUnit,
      pricingMode: base.pricingMode,
      basePrice: base.basePrice,
      durationMinutes: base.durationMinutes,
      minQuantity: 1,
      maxQuantity: 10,
      serviceScope: '室内常规区域：客厅、卧室、厨房、卫生间、阳台',
      serviceStandardSummary: '6 项服务标准，逐项验收',
      version: base.version,
      status: base.status,
      options: [
        {
          optionCode: 'ADDON',
          optionName: '增值服务',
          selectionType: 'MULTI',
          required: false,
          minSelect: 0,
          maxSelect: 3,
          values: [
            {
              valueCode: 'ADDON_FRIDGE',
              valueName: '冰箱内部清洗',
              numericValue: 1,
              extraPrice: 80,
              durationDelta: 30
            },
            {
              valueCode: 'ADDON_GLASS',
              valueName: '玻璃清洗',
              numericValue: 1,
              extraPrice: 60,
              durationDelta: 20
            },
            {
              valueCode: 'ADDON_HOOD',
              valueName: '油烟机拆洗',
              numericValue: 1,
              extraPrice: 120,
              durationDelta: 40
            }
          ]
        }
      ],
      tiers: [],
      standards: [
        {
          standardGroup: '客厅与卧室',
          itemName: '地面除尘拖洗',
          description: '含家具底部与踢脚线',
          required: true,
          evidenceRequired: true,
          sortOrder: 10
        },
        {
          standardGroup: '客厅与卧室',
          itemName: '家具表面擦拭',
          description: '不含贵重物品内部',
          required: true,
          evidenceRequired: false,
          sortOrder: 20
        },
        {
          standardGroup: '厨房',
          itemName: '台面与灶具清洁',
          description: '去油污处理',
          required: true,
          evidenceRequired: true,
          sortOrder: 30
        },
        {
          standardGroup: '卫生间',
          itemName: '洁具除垢消毒',
          description: '马桶、洗手台、淋浴区',
          required: true,
          evidenceRequired: true,
          sortOrder: 40
        },
        {
          standardGroup: '全屋',
          itemName: '垃圾清运',
          description: '服务结束后带走垃圾',
          required: true,
          evidenceRequired: false,
          sortOrder: 50
        },
        {
          standardGroup: '全屋',
          itemName: '服务后拍照留档',
          description: '关键区域各一张',
          required: true,
          evidenceRequired: true,
          sortOrder: 60
        }
      ]
    };
    return detail;
  }

  /** 把扁平 standards 按 standardGroup 聚合成详情页要的分组结构。 */
  static groupStandards(detail: ServiceItemDetail): StandardGroup[] {
    const groups: StandardGroup[] = [];
    for (let i = 0; i < detail.standards.length; i++) {
      const item = detail.standards[i];
      let found: StandardGroup | null = null;
      for (let j = 0; j < groups.length; j++) {
        if (groups[j].groupName === item.standardGroup) {
          found = groups[j];
        }
      }
      if (found === null) {
        const created: StandardGroup = { groupName: item.standardGroup, items: [item] };
        groups.push(created);
      } else {
        found.items.push(item);
      }
    }
    return groups;
  }

  static bookings(): BookingSummary[] {
    const list: BookingSummary[] = [
      {
        orderNo: 'BK202610240001',
        serviceName: '日常保洁 · 两居室',
        orderStatus: 'CONFIRMED',
        paymentStatus: 'SUCCESS',
        appointmentDate: '2026-10-26',
        appointmentStart: '2026-10-26T09:00:00',
        payableAmount: 199,
        createdAt: '2026-10-24T10:12:00'
      },
      {
        orderNo: 'BK202610230007',
        serviceName: '深度保洁 · 按面积',
        orderStatus: 'PENDING_PAYMENT',
        paymentStatus: 'CREATED',
        appointmentDate: '2026-10-27',
        appointmentStart: '2026-10-27T14:00:00',
        payableAmount: 399,
        createdAt: '2026-10-23T19:40:00'
      },
      {
        orderNo: 'BK202610200003',
        serviceName: '日常保洁 · 单间',
        orderStatus: 'COMPLETED',
        paymentStatus: 'SUCCESS',
        appointmentDate: '2026-10-21',
        appointmentStart: '2026-10-21T10:00:00',
        payableAmount: 139,
        createdAt: '2026-10-20T08:30:00'
      },
      {
        orderNo: 'BK202610180011',
        serviceName: '油烟机深度拆洗',
        orderStatus: 'IN_SERVICE',
        paymentStatus: 'SUCCESS',
        appointmentDate: '2026-10-24',
        appointmentStart: '2026-10-24T13:00:00',
        payableAmount: 120,
        createdAt: '2026-10-18T16:20:00'
      }
    ];
    return list;
  }

  static bookingDetail(orderNo: string): BookingDetail {
    const list: BookingSummary[] = MockData.bookings();
    let summary: BookingSummary = list[0];
    for (let i = 0; i < list.length; i++) {
      if (list[i].orderNo === orderNo) {
        summary = list[i];
      }
    }
    const detail: BookingDetail = {
      orderNo: summary.orderNo,
      customerId: 1,
      customerNo: 'C202610240001',
      orderStatus: summary.orderStatus,
      paymentStatus: summary.paymentStatus,
      assignmentStatus: 'ASSIGNED',
      fulfillmentStatus: 'PENDING',
      afterSaleStatus: '',
      reviewStatus: '',
      serviceItemNo: 'SVC_DAILY_2ROOM',
      serviceName: summary.serviceName,
      serviceItemVersion: 1,
      serviceAreaId: 1,
      serviceAreaName: '天河区核心片区',
      appointmentDate: summary.appointmentDate,
      serviceWindowName: '上午 09:00-12:00',
      appointmentStart: summary.appointmentStart,
      appointmentEnd: summary.appointmentStart,
      currentWorkerId: 1,
      workerSlotId: 1,
      serviceAmount: summary.payableAmount,
      addonAmount: 0,
      materialAmount: 0,
      travelFee: 0,
      discountAmount: 0,
      payableAmount: summary.payableAmount,
      paidAmount: summary.paymentStatus === 'SUCCESS' ? summary.payableAmount : 0,
      refundedAmount: 0,
      currency: 'CNY',
      contactName: '陈女士',
      contactPhone: '138****8000',
      detailAddress: '广州市天河区五山街道 华南理工大学 教师公寓 3 栋 502',
      cancelReasonCode: '',
      cancelReasonText: '',
      paymentDeadline: '2026-10-24T13:00:00',
      source: 'APP',
      items: [
        {
          itemType: 'SERVICE',
          itemCode: 'SVC_DAILY_2ROOM',
          itemName: summary.serviceName,
          unitPrice: summary.payableAmount,
          quantity: 1,
          amount: summary.payableAmount
        }
      ],
      createdAt: summary.createdAt,
      paidAt: summary.paymentStatus === 'SUCCESS' ? summary.createdAt : '',
      canceledAt: ''
    };
    return detail;
  }

  static fulfillment(orderNo: string): Fulfillment {
    const data: Fulfillment = {
      id: 1,
      recordNo: 'FR202610240001',
      bookingId: 1,
      workerId: 1,
      status: 'IN_SERVICE',
      departedAt: '2026-10-24T08:32:00',
      checkInAt: '2026-10-24T08:58:00',
      startedAt: '2026-10-24T09:02:00',
      finishedAt: '',
      completionRequestedAt: '',
      confirmationDeadline: '',
      customerConfirmedAt: '',
      serviceResult: ''
    };
    return data;
  }

  static notifications(): Notification[] {
    const list: Notification[] = [
      {
        notificationNo: 'NT202610240001',
        title: '订单已确认',
        content: '您的「日常保洁 · 两居室」订单已确认，服务人员将在 10-26 09:00 上门。',
        businessType: 'BOOKING',
        businessId: 'BK202610240001',
        priority: 'NORMAL',
        readAt: '',
        createdAt: '2026-10-24T10:13:00'
      },
      {
        notificationNo: 'NT202610230002',
        title: '待支付提醒',
        content: '订单 BK202610230007 还未支付，请在 15 分钟内完成支付，超时订单将自动关闭。',
        businessType: 'BOOKING',
        businessId: 'BK202610230007',
        priority: 'HIGH',
        readAt: '',
        createdAt: '2026-10-23T19:41:00'
      },
      {
        notificationNo: 'NT202610200003',
        title: '服务已完成',
        content: '「日常保洁 · 单间」已完成，欢迎对本次服务做出评价。',
        businessType: 'BOOKING',
        businessId: 'BK202610200003',
        priority: 'NORMAL',
        readAt: '2026-10-21T18:00:00',
        createdAt: '2026-10-21T17:30:00'
      },
      {
        notificationNo: 'NT202610180004',
        title: '服务保障升级',
        content: '平台已为全部订单提供「不满意返工」保障，服务后 24 小时内可申请。',
        businessType: 'SYSTEM',
        businessId: '',
        priority: 'LOW',
        readAt: '2026-10-18T09:00:00',
        createdAt: '2026-10-18T08:00:00'
      }
    ];
    return list;
  }

  static addresses(): Address[] {
    const list: Address[] = [
      {
        addressNo: 'AD202610240001',
        label: '家',
        contactName: '陈女士',
        contactPhone: '138****8000',
        region: { cityName: '广州市', districtName: '天河区' },
        communityName: '华工教师公寓',
        buildingNo: '3 栋',
        unitNo: '2 单元',
        floorNo: '5 层',
        roomNo: '502',
        detailAddress: '广州市天河区五山街道 华南理工大学 教师公寓 3 栋 502',
        accessNote: '门禁请按 502',
        isDefault: true,
        areaMatchStatus: 'MATCHED',
        serviceAreaId: 1,
        version: 1
      },
      {
        addressNo: 'AD202610240002',
        label: '公司',
        contactName: '陈女士',
        contactPhone: '138****8000',
        region: { cityName: '广州市', districtName: '天河区' },
        communityName: '珠江新城写字楼',
        buildingNo: 'A 座',
        unitNo: '',
        floorNo: '18 层',
        roomNo: '1803',
        detailAddress: '广州市天河区珠江新城 A 座 1803',
        accessNote: '',
        isDefault: false,
        areaMatchStatus: 'OUT_OF_RANGE',
        serviceAreaId: 0,
        version: 1
      }
    ];
    return list;
  }

  static account(): Account {
    const account: Account = {
      accountNo: 'A202610240001',
      displayName: '陈女士',
      activeRole: 'CUSTOMER',
      status: 'ACTIVE',
      phoneMasked: '138****8000',
      roles: ['CUSTOMER']
    };
    return account;
  }

  static availabilities(): Availability[] {
    const list: Availability[] = [];
    for (let i = 0; i < 7; i++) {
      const date: string = MockData.dateAfter(i);
      const windows: Availability['windows'] = [
        { windowCode: 'AM', windowName: '上午 09:00-12:00', startTime: '09:00:00', endTime: '12:00:00', available: true },
        { windowCode: 'PM', windowName: '下午 14:00-17:00', startTime: '14:00:00', endTime: '17:00:00', available: true },
        { windowCode: 'EVE', windowName: '晚上 18:00-21:00', startTime: '18:00:00', endTime: '21:00:00', available: i % 3 !== 0 }
      ];
      list.push({ serviceDate: date, windows: windows });
    }
    return list;
  }

  /** 订单详情 6 段时间线的演示数据（与真实 Fulfillment 字段对应）。 */
  static timeline(detail: BookingDetail, fulfillment: Fulfillment): TimelineStep[] {
    const steps: TimelineStep[] = [];
    steps.push({ title: '已下单', time: detail.createdAt, state: 'done' });
    steps.push({
      title: '支付成功',
      time: detail.paidAt,
      state: detail.paidAt.length > 0 ? 'done' : 'todo'
    });
    steps.push({
      title: '服务人员出发',
      time: fulfillment.departedAt,
      state: fulfillment.departedAt.length > 0 ? 'done' : 'todo'
    });
    steps.push({
      title: '到达并签到',
      time: fulfillment.checkInAt,
      state: fulfillment.checkInAt.length > 0 ? 'done' : 'todo'
    });
    steps.push({
      title: '服务完成',
      time: fulfillment.finishedAt,
      state: fulfillment.finishedAt.length > 0 ? 'done' : 'todo'
    });
    steps.push({
      title: '确认完成',
      time: fulfillment.customerConfirmedAt,
      state: fulfillment.customerConfirmedAt.length > 0 ? 'done' : 'todo'
    });
    return steps;
  }

  private static dateAfter(offset: number): string {
    const d: Date = new Date();
    d.setDate(d.getDate() + offset);
    const month: string = d.getMonth() + 1 < 10 ? '0' + (d.getMonth() + 1).toString()
      : (d.getMonth() + 1).toString();
    const day: string = d.getDate() < 10 ? '0' + d.getDate().toString() : d.getDate().toString();
    return d.getFullYear().toString() + '-' + month + '-' + day;
  }
}
