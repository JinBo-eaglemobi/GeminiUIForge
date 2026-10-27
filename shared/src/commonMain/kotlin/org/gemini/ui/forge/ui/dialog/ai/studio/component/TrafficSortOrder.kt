package org.gemini.ui.forge.ui.dialog.ai.studio.component

import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.traffic_sort_req_first
import geminiuiforge.composeapp.generated.resources.traffic_sort_size_desc
import geminiuiforge.composeapp.generated.resources.traffic_sort_time_asc
import geminiuiforge.composeapp.generated.resources.traffic_sort_time_desc
import org.jetbrains.compose.resources.StringResource

/**
 * 报文列表排序维度定义
 */
enum class TrafficSortOrder(val labelRes: StringResource) {
    TIME_ASC(Res.string.traffic_sort_time_asc),
    TIME_DESC(Res.string.traffic_sort_time_desc),
    REQ_FIRST(Res.string.traffic_sort_req_first),
    SIZE_DESC(Res.string.traffic_sort_size_desc)
}
