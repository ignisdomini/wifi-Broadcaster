<?php
declare(strict_types=1);

/** Алфавит коротких кодов без визуально похожих символов (нет 0, o, 1, l, i). */
const EFIR_CODE_ALPHABET = '23456789abcdefghjkmnpqrstuvwxyz';
const EFIR_CODE_LENGTH = 5;

function e(?string $value): string
{
    return htmlspecialchars((string)$value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function json_response(array $payload, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function json_error(string $message, string $code, int $status = 400): never
{
    json_response(['ok' => false, 'error' => $message, 'code' => $code], $status);
}

/**
 * Случайный короткий код. Уникальность проверяется вызывающей стороной —
 * при коллизии просто генерируем следующий.
 */
function generate_code(int $length = EFIR_CODE_LENGTH): string
{
    $alphabet = EFIR_CODE_ALPHABET;
    $max = strlen($alphabet) - 1;
    $code = '';
    for ($i = 0; $i < $length; $i++) {
        $code .= $alphabet[random_int(0, $max)];
    }
    return $code;
}

function is_valid_code(string $code): bool
{
    return (bool)preg_match('/^[' . EFIR_CODE_ALPHABET . ']{3,16}$/', $code);
}

function client_ip(): string
{
    return (string)($_SERVER['REMOTE_ADDR'] ?? '0.0.0.0');
}

/**
 * IP хранится только в виде хеша: для лимитов этого достаточно,
 * а исходный адрес в базе не нужен.
 */
function ip_hash(): string
{
    $config = efir_config();
    $salt = (string)($config['ip_salt'] ?? 'efir');
    return hash('sha256', $salt . '|' . client_ip());
}

function base_url(): string
{
    $configured = trim((string)setting('base_url', ''));
    if ($configured !== '') {
        return rtrim($configured, '/') . '/';
    }
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = (string)($_SERVER['HTTP_HOST'] ?? 'localhost');
    return $scheme . '://' . $host . '/';
}

function post_url(string $code): string
{
    return base_url() . 'p/' . $code;
}

/**
 * Адрес личной ленты. Нигде на сайте не выводится и не индексируется:
 * попасть сюда можно, только получив код через эфир.
 */
function profile_url(string $profileCode): string
{
    return base_url() . 'u/' . $profileCode;
}

/** «5 минут назад», «вчера» — для личных лент. */
function human_time(string $datetime): string
{
    $ts = strtotime($datetime);
    if ($ts === false) {
        return $datetime;
    }
    $diff = time() - $ts;
    if ($diff < 60) {
        return 'только что';
    }
    if ($diff < 3600) {
        $m = (int)floor($diff / 60);
        return $m . ' ' . plural($m, 'минуту', 'минуты', 'минут') . ' назад';
    }
    if ($diff < 86400) {
        $h = (int)floor($diff / 3600);
        return $h . ' ' . plural($h, 'час', 'часа', 'часов') . ' назад';
    }
    return date('d.m.Y H:i', $ts);
}

function plural(int $n, string $one, string $few, string $many): string
{
    $mod100 = $n % 100;
    if ($mod100 >= 11 && $mod100 <= 14) {
        return $many;
    }
    return match ($n % 10) {
        1 => $one,
        2, 3, 4 => $few,
        default => $many,
    };
}

function format_bytes(int $bytes): string
{
    if ($bytes >= 1048576) {
        return round($bytes / 1048576, 1) . ' МБ';
    }
    if ($bytes >= 1024) {
        return round($bytes / 1024) . ' КБ';
    }
    return $bytes . ' Б';
}


/**
 * Приложение для загрузки с сайта, если оно туда положено.
 *
 * Файл ищется в /app и наружу отдаётся по прямой ссылке: сборка большая и
 * меняется редко, гонять её через PHP незачем. Нет файла — нет и кнопки,
 * никакой битой ссылки на странице не появится.
 *
 * @return array{url:string, size:string, name:string}|null
 */
function apk_download(): ?array
{
    $dir = EFIR_ROOT . '/app';
    if (!is_dir($dir)) {
        return null;
    }

    $files = glob($dir . '/*.apk') ?: [];
    if (!$files) {
        return null;
    }

    // Если положили несколько сборок, берём самую свежую.
    usort($files, static fn(string $a, string $b): int => filemtime($b) <=> filemtime($a));
    $path = $files[0];
    $name = basename($path);

    return [
        'url'  => '/app/' . rawurlencode($name),
        'size' => format_bytes((int)filesize($path)),
        'name' => $name,
    ];
}

/**
 * Кнопка «Доступно в RuStore».
 *
 * Значок здесь наш, нейтральный, а не фирменный знак магазина: рисовать чужой
 * логотип по памяти нельзя. Если понадобится официальный бейдж — положите
 * картинку в /assets/rustore.svg, и функция подставит её вместо значка,
 * ничего больше менять не придётся.
 */
function store_badge(string $url, string $label = 'Доступно в', string $name = 'RuStore'): string
{
    $official = EFIR_ROOT . '/assets/rustore.svg';

    $mark = is_file($official)
        ? '<img class="store-i" src="/assets/rustore.svg" alt="" width="26" height="26">'
        : '<svg class="store-i" viewBox="0 0 24 24" fill="none" aria-hidden="true">'
            . '<rect x="2.5" y="2.5" width="19" height="19" rx="5.5" '
            . 'stroke="currentColor" stroke-width="1.6"/>'
            . '<path d="M12 7v8m0 0 3.2-3.2M12 15l-3.2-3.2" stroke="currentColor" '
            . 'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'
            . '</svg>';

    return '<a class="store" href="' . e($url) . '" target="_blank" rel="noopener">'
        . $mark
        . '<span><b>' . e($label) . '</b>' . e($name) . '</span>'
        . '</a>';
}
