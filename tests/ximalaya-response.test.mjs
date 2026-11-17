import test from 'node:test';
import assert from 'node:assert/strict';
import {ximalayaResponseError} from '../app/src/main/assets/ximalaya-response.mjs';

test('the observed platform regional copyright refusal gets a precise safe explanation',()=>{
 const error=ximalayaResponseError({ret:927,msg:'很抱歉，由于版权方要求，您所在的地区/国家暂时无法使用该资源 https://cdn.example/private?token=secret'});
 assert.equal(error.code,'unavailable');assert.match(error.message,/地区版权限制/);assert.doesNotMatch(error.message,/会员|secret|token|https:/);
 assert.match(ximalayaResponseError({ret:'927',message:'版权要求，国家限制'}).message,/地区版权限制/);
});
test('unknown refusals, login challenges and successful responses retain upstream handling',()=>{
 for(const body of [null,{}, {ret:927,msg:'未知错误'}, {ret:927,msg:'请完成地区验证'}, {ret:401,msg:'版权地区'}, {ret:403,msg:'版权地区'}, {ret:0,msg:'版权地区'}, {ret:927,msg:{text:'版权地区'}}])assert.equal(ximalayaResponseError(body),null);
});
