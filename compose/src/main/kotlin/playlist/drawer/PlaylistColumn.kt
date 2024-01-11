package playlist.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import playlist.PlaylistMedia

@Composable
fun PlaylistColumn(
    items: List<PlaylistMedia>,
    modifier: Modifier = Modifier,
    itemContent: @Composable LazyItemScope.(PlaylistMedia) -> Unit,
) {

    val listState = rememberLazyListState()

    LaunchedEffect(items) {
        if (items.isNotEmpty()) listState.scrollToItem(0)
    }

    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = listState
    ) {
        items(items = items, itemContent = itemContent, key = PlaylistMedia::media)
    }
}