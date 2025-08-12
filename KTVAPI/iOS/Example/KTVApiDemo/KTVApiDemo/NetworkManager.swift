//
//  NetworkManager.swift
//  Scene-Examples
//
//  Created by zhaoyongqiang on 2021/11/19.
//
import UIKit

public let kAppProjectName = "appProject"
public let kAppProjectValue = "agora_ent_demo"
public let kAppOS = "appOs"
public let kAppOSValue = "iOS"
public let kAppVersion = "versionName"
public let kAppAuthHeader = ""

@objc
class NetworkManager:NSObject {
    @objc public enum TokenGeneratorType: Int {
        case token006 = 0
        case token007 = 1
    }
    
    @objc public enum AgoraTokenType: Int {
        case rtc = 1
        case rtm = 2
        case chat = 3
    }

    enum HTTPMethods: String {
        case GET
        case POST
    }

    typealias SuccessClosure = ([String: Any]) -> Void
    typealias FailClosure = (String) -> Void

    private var sessionConfig: URLSessionConfiguration = {
        let config = URLSessionConfiguration.default
        config.httpAdditionalHeaders = [
            "Content-Type": "application/json",
            "X-LC-Id": "fkUjxadPMmvYF3F3BI4uvmjo-gzGzoHsz",
            "X-LC-Key": "QAvFS62IOR28GfSFQO5ze45s",
            "X-LC-Session": "qmdj8pdidnmyzp0c7yqil91oc",
            kAppProjectName: kAppProjectValue,
            kAppOS: kAppOSValue,
            kAppVersion: "0.0.1"
        ]
        if kAppAuthHeader.count  > 0  {
            config.httpAdditionalHeaders?["Authorization"] = kAppAuthHeader
        }
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 30
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        return config
    }()

    @objc static let shared = NetworkManager()
    private let baseUrl = "https://agoraktv.xyz/1.1/functions/"
    private let baseServerUrl: String = "https://service.shengwang.cn/toolbox/v2/"
    
    private func basicAuth(key: String, password: String) -> String {
        let loginString = String(format: "%@:%@", key, password)
        guard let loginData = loginString.data(using: String.Encoding.utf8) else {
            return ""
        }
        let base64LoginString = loginData.base64EncodedString()
        return base64LoginString
    }
    
    /// get tokens
    /// - Parameters:
    ///   - channelName: <#channelName description#>
    ///   - uid: <#uid description#>
    ///   - tokenGeneratorType: token types
    ///   - tokenTypes: [token type :  token string]
    func generateTokens(channelName: String,
                        uid: String,
                        tokenGeneratorType: TokenGeneratorType,
                        tokenTypes: [AgoraTokenType],
                        success: @escaping ([Int: String]) -> Void)
    {
        let group = DispatchGroup()
        var tokenMap: [Int: String] = [Int:String]()
        
        tokenTypes.forEach { type in
            group.enter()
            generateToken(channelName: channelName,
                          uid: uid,
                          tokenType: tokenGeneratorType,
                          type: type) { token in
                if let token = token, token.count > 0 {
                    tokenMap[type.rawValue] = token
                }
                group.leave()
            }
        }

        group.notify(queue: DispatchQueue.main) {
            success(tokenMap)
        }
    }

    @objc
    func generateToken(channelName: String,
                       uid: String,
                       tokenType: TokenGeneratorType,
                       type: AgoraTokenType,
                       expire: UInt = 1500,
                       success: @escaping (String?) -> Void)
    {
        
        let date = Date()
        let timeInterval = date.timeIntervalSince1970
        let millisecond = CLongLong(timeInterval * 1000)
        
        let params = ["appCertificate": KeyCenter.Certificate ?? "",
                      "appId": KeyCenter.AppId,
                      "channelName": channelName,
                      "expire": expire,
                      "src": "iOS",
                      "ts": millisecond,
                      "type": type.rawValue,
                      "uid": uid] as [String: Any]
        let url = tokenType == .token006 ?
        "\(baseServerUrl)token006/generate"
        : "\(baseServerUrl)token/generate"
        NetworkManager.shared.postRequest(urlString: url,
                                          params: params,
                                          success: { response in
            let data = response["data"] as? [String: String]
            let token = data?["token"]
            print(response)
            success(token)
        }, failure: { error in
            print(error)
            success(nil)
        })
    }

    func getRequest(urlString: String, success: SuccessClosure?, failure: FailClosure?) {
        DispatchQueue.global().async {
            self.request(urlString: urlString, params: nil, method: .GET, success: success, failure: failure)
        }
    }

    func postRequest(urlString: String, params: [String: Any]?, success: SuccessClosure?, failure: FailClosure?) {
        DispatchQueue.global().async {
            self.request(urlString: urlString, params: params, method: .POST, success: success, failure: failure)
        }
    }

    private func request(urlString: String,
                         params: [String: Any]?,
                         method: HTTPMethods,
                         success: SuccessClosure?,
                         failure: FailClosure?)
    {
        let session = URLSession(configuration: sessionConfig)
        guard let request = getRequest(urlString: urlString,
                                       params: params,
                                       method: method,
                                       success: success,
                                       failure: failure) else { return }
        session.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                self.checkResponse(response: response, data: data, success: success, failure: failure)
            }
        }.resume()
    }

    private func getRequest(urlString: String,
                            params: [String: Any]?,
                            method: HTTPMethods,
                            success: SuccessClosure?,
                            failure: FailClosure?) -> URLRequest?
    {
        let string = urlString.hasPrefix("http") ? urlString : baseUrl.appending(urlString)
        guard let url = URL(string: string) else {
            return nil
        }
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        if method == .POST {
            request.httpBody = try? JSONSerialization.data(withJSONObject: params ?? [],
                                                           options: .sortedKeys) // convertParams(params: params).data(using: .utf8)
        }
        let curl = request.cURL(pretty: true)
        #if DEBUG
        debugPrint("curl == \(curl)")
        #endif
        return request
    }

    private func convertParams(params: [String: Any]?) -> String {
        guard let params = params else { return "" }
        let value = params.map({ String(format: "%@=%@", $0.key, "\($0.value)") }).joined(separator: "&")
        return value
    }

    private func checkResponse(response: URLResponse?, data: Data?, success: SuccessClosure?, failure: FailClosure?) {
        if let httpResponse = response as? HTTPURLResponse {
            switch httpResponse.statusCode {
            case 200...201:
                if let resultData = data {
                    let result = String(data: resultData, encoding: .utf8)
                    if let res = result {
                        if let jsonData = res.data(using: .utf8) {
                            do {
                                if let dict = try JSONSerialization.jsonObject(with: jsonData, options: []) as? [String: Any] {
                                    // 使用转换后的字典
                                    print(dict)
                                    success?(dict)
                                }
                            } catch {
                                print("转换失败：\(error)")
                            }
                        }
                    }
                    
                } else {
                    failure?("Error in the request status code \(httpResponse.statusCode), response: \(String(describing: response))")
                }
            default:
                failure?("Error in the request status code \(httpResponse.statusCode), response: \(String(describing: response))")
            }
        } else {
            failure?("Error in the request status code \(400), response: \(String(describing: response))")
        }
    }
}

public extension URLRequest {
    func cURL(pretty: Bool = false) -> String {
        let newLine = pretty ? "\\\n" : ""
        let method = (pretty ? "--request " : "-X ") + "\(httpMethod ?? "GET") \(newLine)"
        let url: String = (pretty ? "--url " : "") + "\'\(url?.absoluteString ?? "")\' \(newLine)"

        var cURL = "curl "
        var header = ""
        var data = ""

        if let httpHeaders = allHTTPHeaderFields, httpHeaders.keys.count > 0 {
            for (key, value) in httpHeaders {
                header += (pretty ? "--header " : "-H ") + "\'\(key): \(value)\' \(newLine)"
            }
        }

        if let bodyData = httpBody, let bodyString = String(data: bodyData, encoding: .utf8), !bodyString.isEmpty {
            data = "--data '\(bodyString)'"
        }

        cURL += method + url + header + data

        return cURL
    }
}

