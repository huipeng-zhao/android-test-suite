package ai.looki.ble.common

import java.util.UUID

object Constants {
    // 自定义服务UUID（避免与标准服务冲突）
    const val SERVICE_UUID = "0000FF00-0000-1000-8000-00805F9B34FB"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"

    // 特征定义
    const val CHAR_CONTROL_UUID = "0000FF01-0000-1000-8000-00805F9B34FB" // 控制指令
    const val CHAR_UPLINK_UUID = "0000FF02-0000-1000-8000-00805F9B34FB"  // DEVO→手机
    const val CHAR_DOWNLINK_UUID = "0000FF03-0000-1000-8000-00805F9B34FB" // 手机→DEVO

    // 控制指令（1字节）
    const val CMD_START_UPLINK: Byte = 0x01    // 启动上行测试
    const val CMD_START_DOWNLINK: Byte = 0x02  // 启动下行测试
    const val CMD_STOP_TEST: Byte = 0x00       // 停止测试
    const val CMD_STOP_UPLINK: Byte = 0x11       // 停止测试
    const val CMD_STOP_DOWNLINK: Byte = 0x22       // 停止测试

    // MTU配置（需双方协商一致）
    const val DEFAULT_MTU = 512

    // 测试数据填充字节
    const val TEST_DATA_BYTE: Byte = 0x55 // 01010101（便于校验）

    // 调试log标志
    const val DEVO_TAG: String = "DEVO "
    const val APP_TAG: String = "APP "
}