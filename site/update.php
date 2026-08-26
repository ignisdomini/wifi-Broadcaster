<?php
declare(strict_types=1);

/**
 * Обновление уже установленной сети.
 *
 * Порядок такой: файлы вы перезаписываете сами (по FTP или из панели), не
 * трогая config.php, а этот скрипт доводит до ума остальное — схему базы и
 * остатки прежних версий. Запускать можно сколько угодно раз: всё, что уже
 * сделано, пропускается.
 *
 * Данные скрипт не удаляет. Про то, что осталось лишним, он сообщает, а
 * решение принимает владелец сети.
 */

require_once __DIR__ . '/lib/bootstrap.php';

$isCli = PHP_SAPI === 'cli';

if (!efir_is_installed()) {
    $message = 'Сеть ещё не установлена — откройте install.php.';
    if ($isCli) {
        exit($message . PHP_EOL);
    }
    header('Location: /install.php');
    exit;
}

require_once __DIR__ . '/lib/migrations.php';

// Из браузера обновление пускает только администратор: оно меняет схему базы.
if (!$isCli && !admin_is_logged_in()) {
    header('Location: /admin/');
    exit;
}

$done = false;
$log = [];
$warnings = [];
$error = null;

$shouldRun = $isCli || ($_SERVER['REQUEST_METHOD'] === 'POST');
if ($shouldRun && !$isCli) {
    csrf_check();
}

if ($shouldRun) {
    try {
        $log = run_migrations();
        $log = array_merge($log, cleanup_obsolete_files());

        if (!$log) {
            $log[] = 'менять было нечего — схема и файлы уже в порядке';
        }

        // Настройки, появившиеся в новых версиях, отдельно заводить не нужно:
        // всё, чего нет в базе, берётся из значений по умолчанию.
        $warnings = leftover_warnings();
        $done = true;
    } catch (Throwable $e) {
        $error = $e->getMessage();
    }
}

if ($isCli) {
    if ($error !== null) {
        echo 'ОШИБКА: ', $error, PHP_EOL;
        exit(1);
    }
    foreach ($log as $line) {
        echo '  ', $line, PHP_EOL;
    }
    foreach ($warnings as $line) {
        echo '  ! ', $line, PHP_EOL;
    }
    echo 'Готово. Версия движка: ', EFIR_VERSION, PHP_EOL;
    exit;
}

$siteName = (string)setting('site_name');
?>
<!doctype html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Обновление — <?= e($siteName) ?></title>
    <link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">
    <link rel="stylesheet" href="/assets/style.css">
</head>
<body class="page-plain">
<div class="install">
    <span class="brand-mark"><?= e($siteName) ?></span>
    <p class="muted">Обновление · движок <?= e(EFIR_VERSION) ?></p>

    <?php if ($error !== null): ?>
        <div class="notice notice-error">
            <p><strong>Не получилось.</strong></p>
            <p><?= e($error) ?></p>
            <p class="muted">База не тронута настолько, насколько шаг не успел выполниться.
                Исправьте причину и запустите обновление снова.</p>
        </div>
    <?php elseif ($done): ?>
        <div class="notice notice-ok">
            <p><strong>Готово.</strong></p>
            <?php foreach ($log as $line): ?>
                <p>· <?= e($line) ?></p>
            <?php endforeach; ?>
        </div>

        <?php foreach ($warnings as $line): ?>
            <div class="notice notice-warn"><p><?= e($line) ?></p></div>
        <?php endforeach; ?>

        <p><a href="/admin/">В штаб</a> · <a href="/">На главную</a></p>
    <?php else: ?>
        <div class="notice">
            <p>Перед запуском:</p>
            <p>1. Перезапишите файлы новой версией, <strong>не трогая config.php</strong>.</p>
            <p>2. Сделайте копию базы, если она вам дорога, — обновление меняет схему.</p>
            <p class="muted">
                Скрипт добавит недостающие таблицы и колонки, уберёт файлы прежних
                версий и скажет, что осталось лишним. Ваши записи, позывные и
                настройки он не трогает.
            </p>
        </div>

        <form method="post" class="form">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <button class="btn" type="submit">Обновить</button>
        </form>
    <?php endif; ?>
</div>
</body>
</html>
