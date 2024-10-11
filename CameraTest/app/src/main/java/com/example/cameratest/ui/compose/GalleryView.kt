package com.example.cameratest.ui.compose

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cameratest.R
import com.example.cameratest.data.MediaStoreImage
import com.example.cameratest.utils.StorageUtil
import com.example.cameratest.viewmodel.CameraViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryView(context: Context,
                viewModel: CameraViewModel,
                back: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val storageUtil = StorageUtil()
    val listData by viewModel.images.observeAsState()
    var selectFileName by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<MediaStoreImage?>(null) }
    var isShowDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.loadImages(context)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color.Black)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_back), contentDescription = null,
            modifier = Modifier.padding(5.dp,15.dp).clickable {
                back()
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp, 60.dp, 0.dp, 40.dp)
        ) {
            LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                listData?.let {
                    items(it.size) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            listData!![it].bitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.combinedClickable(
                                        onClick = {
                                            // 获取文件名
                                            selectFileName = listData!![it].displayName
                                            // 获取文件byte array
                                            storageUtil.uriToByteArray(
                                                listData!![it].contentUri,
                                                context
                                            )
                                        },
                                        onLongClick = {
                                            deleteItem = listData!![it]
                                            isShowDialog = true
                                        }

                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.select_file_name) + " " + selectFileName,
            color = Color(0xffffffff),
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(0.dp, 0.dp, 0.dp, 10.dp)
        )

        if (isShowDialog) {
            AlertDialog(
                title = {
                    Text(text = stringResource(R.string.dialog_title_text_delete))
                },
                text = {
                    Text(text = "Delete ${deleteItem!!.displayName}?")
                },
                onDismissRequest = {
                    isShowDialog = false
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                storageUtil.performDeleteImage(context, deleteItem!!)
                                viewModel.loadImages(context)
                            }
                            isShowDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.dialog_confirm_text))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            isShowDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.dialog_dissmiss_text))
                    }
                }
            )
        }
    }
}
