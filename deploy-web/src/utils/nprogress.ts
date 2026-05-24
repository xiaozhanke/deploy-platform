import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
// 自定义 NProgress 样式
import '@/styles/nprogress.scss'

// 配置 NProgress
NProgress.configure({
  // 初始化时的最小百分比
  minimum: 0.3,
  // 动画方式
  easing: 'ease',
  // 递增进度条的速度
  speed: 500,
  // 开启自动递增
  trickle: true,
  // 自动递增速度
  trickleSpeed: 200,
  // 是否显示加载旋转器
  showSpinner: false,
})

export default NProgress
