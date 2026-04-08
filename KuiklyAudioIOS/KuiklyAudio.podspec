Pod::Spec.new do |s|
  s.name             = 'KuiklyAudio'
  s.version          = '1.0.0'
  s.summary          = 'Kuikly 音频播放组件 iOS 原生实现'
  s.description      = <<-DESC
    基于 AVPlayer 的 Kuikly 音频播放 Module，支持播放控制、播放列表、后台播放、锁屏控制、音量倍速等功能。
  DESC

  s.homepage         = 'https://github.com/user/KuiklyAudio'
  s.license          = { :type => 'MIT' }
  s.author           = { 'Kuikly' => 'kuikly@tencent.com' }
  s.source           = { :git => 'https://github.com/user/KuiklyAudio.git', :tag => s.version.to_s }

  s.ios.deployment_target = '13.0'
  s.source_files = 'Classes/**/*'
  s.frameworks = 'AVFoundation', 'MediaPlayer', 'UIKit'

  # 依赖 Kuikly iOS 渲染层（提供 KuiklyRenderBaseModule 基类）
  s.dependency 'OpenKuiklyIOSRender', '~> 2.7.0'
end
