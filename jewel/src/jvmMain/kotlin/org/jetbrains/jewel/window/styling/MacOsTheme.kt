package org.jetbrains.jewel.window.styling

import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

class MacOsTheme {
    object WindowControls {
        /** 24x24  */
        val Close: IntelliJIconKey =
            IntelliJIconKey("mac/theme/close.svg", "mac/theme/close.svg", AllIconsKeys::class.java)

        /** 24x24  */
        val CloseHover: IntelliJIconKey =
            IntelliJIconKey(
                "mac/theme/close-hover.svg",
                "mac/theme/close-hover.svg",
                AllIconsKeys::class.java,
            )

        /** 24x24  */
        val Maximize: IntelliJIconKey =
            IntelliJIconKey(
                "mac/theme/maximize.svg",
                "mac/theme/maximize.svg",
                AllIconsKeys::class.java,
            )

        /** 24x24  */
        val MaximizeHover: IntelliJIconKey =
            IntelliJIconKey(
                "mac/theme/maximize-hover.svg",
                "mac/theme/maximize-hover.svg",
                AllIconsKeys::class.java,
            )

        /** 24x24  */
        val Minimize: IntelliJIconKey =
            IntelliJIconKey(
                "linux/theme/gnome/minimize.svg",
                "linux/theme/gnome/minimize.svg",
                AllIconsKeys::class.java,
            )

        /** 24x24  */
        val MinimizeHover: IntelliJIconKey =
            IntelliJIconKey(
                "mac/theme/minimize.svg",
                "mac/theme/minimize.svg",
                AllIconsKeys::class.java,
            )
    }
}
