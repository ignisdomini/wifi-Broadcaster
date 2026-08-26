<?php
declare(strict_types=1);

/**
 * Иконка 512×512 для карточки в магазине приложений.
 *
 *     php store/make-icon.php
 *
 * Рисуется тем же знаком, что и `res/drawable/ic_launcher.xml` — антенной с
 * расходящимися волнами, — только растром: магазины векторов не принимают.
 * Координаты взяты из вектора (холст 108) и масштабированы, поэтому при
 * правке значка надо править оба места.
 *
 * Штрихи ставятся точками по пути, а не `imagearc`: у GD дуга с толщиной
 * получается рваной по краям. Рисуем вчетверо крупнее и ужимаем — так края
 * выходят гладкими без всякого сглаживания на лету.
 */

const SIZE = 512;
const SUPERSAMPLE = 4;
const SOURCE_CANVAS = 108.0;

$big = SIZE * SUPERSAMPLE;
$scale = $big / SOURCE_CANVAS;

$image = imagecreatetruecolor($big, $big);

$background = imagecolorallocate($image, 0x07, 0x0A, 0x08);
imagefilledrectangle($image, 0, 0, $big, $big, $background);

$signal = imagecolorallocate($image, 0x7C, 0xFF, 0xB2);

/** Толщина штриха из вектора — 5 единиц холста. */
$stroke = 5 * $scale;

/** Ставит круглую точку пера в точке холста-источника. */
$dot = static function (float $x, float $y) use ($image, $signal, $scale, $stroke): void {
    imagefilledellipse(
        $image,
        (int)round($x * $scale),
        (int)round($y * $scale),
        (int)round($stroke),
        (int)round($stroke),
        $signal
    );
};

/**
 * Дуга по хорде: от $x1 до $x2 на высоте $y, радиусом $r, выпуклостью вверх —
 * ровно так они заданы в векторе.
 */
$arc = static function (float $x1, float $x2, float $y, float $r) use ($dot): void {
    $half = ($x2 - $x1) / 2;
    $centerX = $x1 + $half;
    $centerY = $y + sqrt(max(0.0, $r * $r - $half * $half));

    $from = atan2($y - $centerY, $x1 - $centerX);
    $to = atan2($y - $centerY, $x2 - $centerX);
    if ($to > $from) {
        [$from, $to] = [$to, $from];
    }

    $steps = 1200;
    for ($i = 0; $i <= $steps; $i++) {
        $angle = $from + ($to - $from) * $i / $steps;
        $dot($centerX + $r * cos($angle), $centerY + $r * sin($angle));
    }
};

// Мачта.
for ($i = 0; $i <= 600; $i++) {
    $dot(54.0, 44.0 + (78.0 - 44.0) * $i / 600);
}

$arc(37, 71, 56, 24);
$arc(24, 84, 44, 42);

// Основание — залитый кружок.
imagefilledellipse(
    $image,
    (int)round(54 * $scale),
    (int)round(74 * $scale),
    (int)round(14 * $scale),
    (int)round(14 * $scale),
    $signal
);

// Ужимаем до нужного размера — здесь и появляется гладкость краёв.
$out = imagecreatetruecolor(SIZE, SIZE);
imagecopyresampled($out, $image, 0, 0, 0, 0, SIZE, SIZE, $big, $big);

$path = __DIR__ . '/icon-512.png';
imagepng($out, $path, 9);
imagedestroy($image);
imagedestroy($out);

echo 'Иконка: ', $path, ' (', round((int)filesize($path) / 1024, 1), " КБ)\n";
