<?php
declare(strict_types=1);

/**
 * Сборка архива для хостинга.
 *
 *     php tools/build-dist.php
 *
 * Кладёт в D:\CLAUDE\dist три вещи: архив сайта, APK и «ЧИТАЙ.txt» рядом с
 * ними. Сборка приложения, если она найдена, попадает ещё и внутрь архива —
 * в каталог /app, откуда её отдаёт кнопка на главной.
 *
 * Почему свой ZipArchive, а не готовая утилита: ZipFile::CreateFromDirectory
 * из PowerShell пишет пути с обратными слэшами, и распаковщики на хостингах
 * делают из них файлы с именами вида «lib\db.php» в корне. Здесь разделитель
 * прямой всегда.
 */

const VERSION_FALLBACK = '1.4';

$root = dirname(__DIR__);
$distDir = dirname($root, 2) . '/dist';
$apkSource = dirname($root) . '/android/app/build/outputs/apk/release/app-release.apk';

// Версия движка — единственное место, где она задана.
$bootstrap = (string)file_get_contents($root . '/lib/bootstrap.php');
preg_match("/EFIR_VERSION',\s*'([^']+)'/", $bootstrap, $m);
$version = $m[1] ?? VERSION_FALLBACK;

// Приложение версионируется отдельно: оно меняется чаще движка, и подписывать
// сборку номером сайта было бы враньём.
$gradle = @file_get_contents(dirname($root) . '/android/app/build.gradle.kts') ?: '';
preg_match('/versionName\s*=\s*"([^"]+)"/', $gradle, $am);
$appVersion = $am[1] ?? $version;

// Каталог app пропускаем: сборку в архив кладём отдельно и под своим именем,
// иначе локально положенный APK попал бы туда вторым файлом.
$skipDirs = ['tools', 'app', '.git', 'node_modules'];
$skipFiles = ['config.php', '.gitignore'];

if (!is_dir($distDir) && !mkdir($distDir, 0777, true) && !is_dir($distDir)) {
    fwrite(STDERR, "Не создать $distDir\n");
    exit(1);
}

// --------------------------------------------------------------- APK

$apkName = "radioinformator-$appVersion.apk";
$apkDist = "$distDir/$apkName";
$hasApk = is_file($apkSource);

if ($hasApk) {
    copy($apkSource, $apkDist);
    echo "APK: $apkName (", round((int)filesize($apkDist) / 1048576, 1), " МБ)\n";
} else {
    echo "APK не найден ($apkSource) — архив будет без кнопки загрузки\n";
}

// --------------------------------------------------------------- архив сайта

$zipPath = "$distDir/radioinformator-site-$version.zip";
@unlink($zipPath);

$zip = new ZipArchive();
if ($zip->open($zipPath, ZipArchive::CREATE) !== true) {
    fwrite(STDERR, "Не открыть $zipPath\n");
    exit(1);
}

$files = [];
$iterator = new RecursiveIteratorIterator(
    new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS),
    RecursiveIteratorIterator::SELF_FIRST
);

/** @var SplFileInfo $item */
foreach ($iterator as $item) {
    $relative = str_replace('\\', '/', substr($item->getPathname(), strlen($root) + 1));
    $top = explode('/', $relative)[0];

    if (in_array($top, $skipDirs, true) || in_array($relative, $skipFiles, true)) {
        continue;
    }
    if ($item->isDir()) {
        continue;
    }
    $files[] = $relative;
}

sort($files);
foreach ($files as $relative) {
    $zip->addFile($root . '/' . $relative, $relative);
}

// Приложение — внутрь архива, чтобы кнопка на главной заработала сразу.
if ($hasApk) {
    $zip->addFile($apkDist, 'app/' . $apkName);
}

$zip->close();

echo "Архив: ", basename($zipPath), ' (', round((int)filesize($zipPath) / 1024), " КБ, ",
    count($files) + ($hasApk ? 1 : 0), " файлов)\n";

// --------------------------------------------------------------- проверка

// Пути с обратным слэшем ломают распаковку на хостинге молча — проверяем.
$check = new ZipArchive();
$check->open($zipPath);
$bad = [];
for ($i = 0; $i < $check->numFiles; $i++) {
    $name = (string)$check->getNameIndex($i);
    if (str_contains($name, '\\')) {
        $bad[] = $name;
    }
}
$mustHave = ['index.php', 'install.php', 'update.php', 'schema.sql', '.htaccess',
    'lib/bootstrap.php', 'lib/contacts.php', 'api/profile.php'];
$missing = array_values(array_filter(
    $mustHave,
    static fn(string $name): bool => $check->locateName($name) === false
));
$leaked = $check->locateName('config.php') !== false;
$check->close();

if ($bad) {
    fwrite(STDERR, "ОШИБКА: обратные слэши в путях: " . implode(', ', $bad) . "\n");
    exit(1);
}
if ($missing) {
    fwrite(STDERR, "ОШИБКА: в архиве нет: " . implode(', ', $missing) . "\n");
    exit(1);
}
if ($leaked) {
    fwrite(STDERR, "ОШИБКА: в архив попал config.php с доступом к базе\n");
    exit(1);
}

echo "Проверка архива: пути прямые, config.php не попал, всё нужное на месте\n";
