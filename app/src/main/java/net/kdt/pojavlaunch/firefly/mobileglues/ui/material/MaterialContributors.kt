@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package net.kdt.pojavlaunch.firefly.mobileglues.ui.material

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AppController
import net.kdt.pojavlaunch.firefly.mobileglues.ui.Contributor
import net.kdt.pojavlaunch.firefly.mobileglues.ui.ContributorGroups
import kotlin.math.max

/**
 * 贡献者致谢：三个仓库各一面头像墙。
 *
 * 头像是随包内置的，不是运行时拉的——本应用没有 INTERNET 权限，而那是隐私政策里
 * 请用户自己去核实的一条。名单因此停在打包的那一刻，这个代价比作废那条承诺小得多。
 */
@Composable
fun MaterialContributorsSection(controller: AppController) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.contributors_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = ScreenPadding + 12.dp,
                top = 20.dp,
                bottom = 4.dp,
            ),
        )
        Text(
            text = stringResource(R.string.contributors_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenPadding + 12.dp),
        )

        ContributorGroups.forEach { group ->
            Text(
                text = stringResource(group.title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = ScreenPadding + 12.dp,
                    top = 16.dp,
                    bottom = 8.dp,
                ),
            )
            // The columns are measured rather than fixed. A fixed cell width
            // leaves whatever does not divide evenly at the end of the row, and
            // FlowRow puts all of it on the right -- at some densities that is
            // nearly a whole column of empty space on one side and none on the
            // other. Fitting as many cells of at least FaceMinWidth as the width
            // allows and then sharing the remainder among them keeps both edges
            // flush, keeps every row on the same columns, and gives the names a
            // little more room before they have to be cut short.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding + 4.dp),
            ) {
                val density = LocalDensity.current
                // Divided in whole pixels, not in dp. A dp cell width is a float,
                // every child rounds it to pixels on its own, and `columns` of
                // them can add up to a few pixels more than the row they were
                // meant to fill -- at which point the last one wraps and leaves a
                // column-wide hole on the right, which is the shape of the
                // problem this is here to avoid. Integer division cannot exceed
                // the row; the leftover is under one pixel per column.
                //
                // Hoisted out of FlowRow, whose scope shadows maxWidth.
                val availablePx = with(density) { maxWidth.roundToPx() }
                val columns = max(1, availablePx / with(density) { FaceMinWidth.roundToPx() })
                val cellWidth = with(density) { (availablePx / columns).toDp() }
                FlowRow(modifier = Modifier.fillMaxWidth()) {
                    group.contributors.forEach { contributor ->
                        ContributorFace(
                            contributor = contributor,
                            width = cellWidth,
                            onClick = { controller.openContributor(contributor) },
                        )
                    }
                }
            }
        }
    }
}

/** 一张头像加一个名字。名字比格子窄的时候截断，不去挤旁边那位。 */
@Composable
private fun ContributorFace(contributor: Contributor, width: Dp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(width)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Image(
            painter = painterResource(contributor.avatar),
            contentDescription = contributor.login,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = contributor.login,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一格至少这么宽；实际宽度按可用宽度均分后略大于它。 */
private val FaceMinWidth = 72.dp


