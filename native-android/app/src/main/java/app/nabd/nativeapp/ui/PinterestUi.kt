package app.nabd.nativeapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.PinItem

private val PinterestRed = Color(0xFFE60023)
private val PillText = Color(0xFF222222)

/**
 * شعار Pinterest: دائرة حمرا وحرف P أبيض. مرسوم بـ Canvas —
 * ما كيحتاج حتى صورة، وهو **العلامة الوحيدة** ديال Pinterest فالواجهة:
 * ما كاين لا راس كبير، لا زر كبير، لا علامة مائية على الصور.
 */
@Composable
fun PinterestGlyph(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        drawCircle(color = PinterestRed, radius = w / 2f)
        // الساق + الكأس: بجوج بيض، والاتحاد ديالهوم كيعطي حرف P.
        val path = Path()
        path.addRect(Rect(Offset(w * 0.36f, w * 0.24f), Size(w * 0.10f, w * 0.54f)))
        path.addOval(Rect(Offset(w * 0.335f, w * 0.21f), Size(w * 0.33f, w * 0.31f)))
        drawPath(path = path, color = Color.White)
    }
}

/** نفس الحبّة البيضاء ديال زر Google: العرض كامل، 56dp، نص غليق. */
@Composable
fun PinterestSignInButton(loading: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White, RoundedCornerShape(28.dp))
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .testTag("pinterest-signin"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PinterestRed, strokeWidth = 2.5.dp)
        } else {
            // فـ RTL هادا هو اللي كيبان على اليمين.
            PinterestGlyph()
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "المتابعة بحساب Pinterest",
            color = if (enabled) PillText else PillText.copy(alpha = 0.45f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * شريط البحث فوق الفيد.
 *
 * البحث كيدور **غير فالصور ديالك** (`/v5/search/pins`) — هادا هو السقف الرسمي
 * ديال API. وما كنقلبوش فـ Pinterest كامل بـ scraping: كنعرضو صف صريح
 * «قلب فـ Pinterest» اللي كيحل المتصفح.
 */
@Composable
fun PinterestSearchBar(query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit, onOpenInPinterest: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).background(Raised, RoundedCornerShape(23.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PinterestGlyph(size = 16.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isBlank()) Text("قلب فالصور ديالك…", color = Muted, fontSize = 13.5.sp, maxLines = 1)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 13.5.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().testTag("pinterest-search"),
                )
            }
            if (query.isNotBlank()) {
                Text("قلب فـ Pinterest", color = PinterestRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenInPinterest).testTag("pinterest-web-search"))
            }
        }
        // إشعار صادق: هاد البحث ماشي هو Pinterest كامل.
        Text("البحث كيدور غير فالصور ديال حسابك فـ Pinterest.", color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp))
    }
}

/**
 * منشور-صورة: الصورة والعنوان، وتحتها شعار Pinterest صغير هو **الإحالة للمصدر**.
 * الضغطة على الشعار ولا على الصورة كتحل الصورة الأصلية فـ Pinterest.
 */
@Composable
fun PinCard(pin: PinItem, saved: Boolean, onOpen: () -> Unit, onDownload: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .background(Raised, RoundedCornerShape(24.dp))
            .testTag("pin-card"),
    ) {
        if (!pin.image.isNullOrBlank()) {
            // الصورة البعيدة كتدوز من NabdImage: نفس الكاش ديال باقي الصور.
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .clickable(role = Role.Button, onClick = onOpen),
            ) {
                NabdImage(pin.image, pin.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pin.title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // العلامة الوحيدة ديال Pinterest: شعار صغير تحت المنشور.
                PinterestGlyph(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpen).testTag("pin-source-logo"),
                    size = 16.dp,
                )
                Text(pin.boardName.ifBlank { "Pinterest" }, color = Muted, fontSize = 12.sp,
                    maxLines = 1, modifier = Modifier.weight(1f))
                Text(
                    if (saved) "تحيّدات ✓" else "تحميل",
                    color = if (saved) Gold else TextMain,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !saved, role = Role.Button, onClick = onDownload)
                        .padding(horizontal = 8.dp, vertical = 4.dp).testTag("pin-download"),
                )
            }
        }
    }
}

/** إلا ما كانش Pinterest مربوط: كارت صغير فوق الفيد، وما كاين حتى غلطة. */
@Composable
fun ConnectPinterestCard(onConnect: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .background(Raised, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onConnect)
            .padding(16.dp).testTag("pinterest-connect"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PinterestGlyph(size = 26.dp)
        Text("ربط حساب Pinterest", color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
        Text("باش تبان الصور ديالك فالفيد. خاصك حساب Pinterest؛ إلا ما كانش عندك كتصاوبو فصفحة Pinterest.",
            color = Muted, fontSize = 12.5.sp, textAlign = TextAlign.Center)
    }
}

/**
 * عرض الربط من بعد الدخول: الصفحة الرسمية ديال Pinterest هي اللي كتخلّي المستخدم
 * يختار الحساب ديالو. التطبيق ما كيصاوب حساب Pinterest — هادشي ما كاينش فالـ API.
 */
@Composable
fun LinkPinterestCard(google: Boolean, busy: Boolean, onLink: () -> Unit, onLater: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .background(Raised, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PinterestGlyph(size = 20.dp)
            Text(
                if (google) "ربط حساب Google" else "ربط حساب Pinterest",
                color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
            )
        }
        Text(
            if (google) "ربط Google كيخلّيك تدخل بجوج. Pinterest غادي يفتح صفحتو باش تختار الحساب."
            else "ربط Pinterest كيجيب الصور ديالك للفيد. Pinterest غادي يفتح صفحتو باش تختار الحساب؛ إلا ما كانش عندك حساب صاوبو تما.",
            color = Muted, fontSize = 12.5.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = PinterestRed, strokeWidth = 2.5.dp)
            else Text("ربط دابا", color = PinterestRed, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(role = Role.Button, onClick = onLink).testTag("pinterest-link"))
            Text("ماشي دابا", color = Muted, fontSize = 13.5.sp,
                modifier = Modifier.clickable(role = Role.Button, onClick = onLater))
        }
    }
}
