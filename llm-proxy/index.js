/**
 * 星眸 LLM 反代函数（veFaaS Node.js 运行时，API 网关触发器）。
 *
 * 职责：把 App 端 POST /chat/completions 请求转发到火山方舟豆包，
 * 注入服务器侧环境变量持有的 ARK_API_KEY，并把 model 字段强制替换成
 * 环境变量里的 ep- 推理接入点 ID。这样 APK 不再持有任何 Key。
 *
 * 环境变量：
 *   ARK_API_KEY    火山方舟 API Key（必填）
 *   ARK_ENDPOINT   ep- 推理接入点 ID（必填，绑死 doubao-lite-4k）
 *   PROXY_TOKEN    可选；设置后 App 端必须带 Authorization: Bearer <token>
 *
 * 事件结构（API 网关触发器）：
 *   { httpMethod, path, headers, queryStringParameters, body }
 * 返回结构：
 *   { statusCode, headers, body }
 */
const https = require('https');

const ARK_API_KEY = process.env.ARK_API_KEY || '';
const ARK_ENDPOINT = process.env.ARK_ENDPOINT || '';
const ARK_BASE = 'https://ark.cn-beijing.volces.com/api/v3';
const PROXY_TOKEN = process.env.PROXY_TOKEN || '';

function jsonResp(statusCode, body, extraHeaders) {
  const headers = {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  };
  if (extraHeaders) Object.assign(headers, extraHeaders);
  return {
    statusCode,
    headers,
    body: typeof body === 'string' ? body : JSON.stringify(body)
  };
}

function forwardToArk(body) {
  return new Promise((resolve) => {
    const payload = Buffer.from(JSON.stringify(body));
    const req = https.request(ARK_BASE + '/chat/completions', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer ' + ARK_API_KEY,
        'Content-Type': 'application/json',
        'Content-Length': payload.length
      }
    }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const text = Buffer.concat(chunks).toString('utf8');
        resolve({
          statusCode: res.statusCode || 502,
          headers: { 'Content-Type': 'application/json; charset=utf-8' },
          body: text
        });
      });
    });
    req.on('error', (e) => {
      resolve(jsonResp(502, { error: 'upstream_error', message: String(e && e.message || e) }));
    });
    req.write(payload);
    req.end();
  });
}

exports.handler = async (rawEvent) => {
  // event 可能是字符串或对象，统一成对象
  let event = rawEvent;
  if (typeof event === 'string' && event.length) {
    try { event = JSON.parse(event); }
    catch (e) { return jsonResp(400, { error: 'invalid_event' }); }
  }
  if (!event || typeof event !== 'object') {
    return jsonResp(400, { error: 'empty_event' });
  }

  // CORS 预检
  if (event.httpMethod === 'OPTIONS' || event.httpMethod === 'GET') {
    return jsonResp(204, '');
  }
  if (event.httpMethod !== 'POST') {
    return jsonResp(405, { error: 'method_not_allowed' });
  }
  if (!ARK_API_KEY || !ARK_ENDPOINT) {
    return jsonResp(500, { error: 'ark_not_configured' });
  }
  // 可选鉴权：App 端带 Bearer token
  if (PROXY_TOKEN) {
    const headers = event.headers || {};
    const auth = headers['Authorization'] || headers['authorization'] || '';
    if (auth !== 'Bearer ' + PROXY_TOKEN) {
      return jsonResp(401, { error: 'unauthorized' });
    }
  }
  let reqBody;
  try {
    reqBody = JSON.parse(event.body || '{}');
  } catch (e) {
    return jsonResp(400, { error: 'invalid_json' });
  }
  // 强制用环境变量里的 ep- 接入点，App 端无法绕过
  reqBody.model = ARK_ENDPOINT;
  return forwardToArk(reqBody);
};
